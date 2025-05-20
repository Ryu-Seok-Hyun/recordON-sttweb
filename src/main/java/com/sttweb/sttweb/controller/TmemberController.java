// src/main/java/com/sttweb/sttweb/controller/TmemberController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TmemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  private final TmemberService svc;
  private final JwtTokenProvider jwtTokenProvider;

  private ResponseEntity<String> checkToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 없습니다.");
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
    }
    return null;
  }

  private Info getMeFromToken(String authHeader) {
    String token = authHeader.substring(7);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  private ResponseEntity<String> checkAdminOrBranchAdmin(String authHeader) {
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;
    Info me = getMeFromToken(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");
    }
    return null;
  }

  @LogActivity(type="member", activity="등록", contents="사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @Valid @RequestBody SignupRequest req
  ) {
    // 1) 본사/지사 관리자만
    ResponseEntity<String> err = checkAdminOrBranchAdmin(authHeader);
    if (err != null) return err;

    // 2) 전역 userId 중복 검사 → 있으면 409로 바로 막기
    if (svc.existsByUserId(req.getUserId())) {
      return ResponseEntity
          .status(HttpStatus.CONFLICT)
          .body("이미 존재하는 ID 입니다.");
    }

    // 3) 호출자 정보
    Info me = getMeFromToken(authHeader);

    // 4) 지사 관리자는 무조건 일반유저(2)
    String originalLevel = req.getUserLevel();
    if ("1".equals(me.getUserLevel())) {
      req.setUserLevel("2");
    }

    // 5) 가입 처리
    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());

    // 6) 권한 변경 안내
    if (!originalLevel.equals(req.getUserLevel())) {
      return ResponseEntity.ok(
          "가입이 완료되었습니다. 지사 관리자는 해당 지사 유저만 생성할 수 있어, "
              + "요청하신 권한(" + originalLevel + ") 대신 일반유저(2)로 처리되었습니다."
      );
    }

    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  // ──────────────────────────────────────────────
  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<Info> login(@Valid @RequestBody LoginRequest req) {
    TmemberEntity user = svc.login(req);
    String token = jwtTokenProvider.createToken(user.getUserId(), user.getUserLevel());
    Info info = Info.fromEntity(user);
    if (info.getBranchSeq() != null) {
      info.setBranchName(svc.getMyInfoByUserId(user.getUserId()).getBranchName());
    }
    info.setToken(token);
    info.setTokenType("Bearer");
    return ResponseEntity.ok(info);
  }

  @LogActivity(type = "member", activity = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<String> logout() {
    svc.logout();
    return ResponseEntity.ok("로그아웃 완료");
  }

  @LogActivity(type = "member", activity = "조회", contents = "내 정보 조회")
  @GetMapping("/me")
  public ResponseEntity<?> getMyInfo(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err2 = checkToken(authHeader);
    if (err2 != null) return err2;
    Info info = getMeFromToken(authHeader);
    return ResponseEntity.ok(info);
  }

  @LogActivity(type="member", activity="조회", contents="전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<?> listOrSearchUsers(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(name="keyword", required=false) String keyword,
      @RequestParam(name="page",    defaultValue="0")  int page,
      @RequestParam(name="size",    defaultValue="10") int size
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 내 정보(userLevel, branchSeq, memberSeq)
    Info me = getMeFromToken(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    // 3) 본사 관리자 → 전체
    if ("0".equals(lvl)) {
      Page<Info> result = (keyword != null && !keyword.isBlank())
          ? svc.searchUsers(keyword.trim(), pr)
          : svc.listAllUsers(pr);
      return ResponseEntity.ok(result);

      // 4) 지사 관리자 → 지사별
    } else if ("1".equals(lvl)) {
      Integer myBranch = me.getBranchSeq();
      if (myBranch == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("내 지사 정보가 없습니다.");
      }
      Page<Info> result = (keyword != null && !keyword.isBlank())
          ? svc.searchUsersInBranch(keyword.trim(), myBranch, pr)
          : svc.listUsersInBranch(myBranch, pr);
      return ResponseEntity.ok(result);

      // 5) 일반 유저 → 본인 정보만
    } else if ("2".equals(lvl)) {
      // 페이지네이션 정보는 쓰지 않고 단일 객체로 반환해도 되지만,
      // 기존 반환 타입(Page)을 유지하려면 아래처럼 PageImpl 을 쓰면됨.
      Info single = me;
      Page<Info> pageOfOne = new org.springframework.data.domain.PageImpl<>(
          java.util.List.of(single), pr, 1
      );
      return ResponseEntity.ok(pageOfOne);

    } else {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }
  }


  @LogActivity(type="member", activity="수정", contents="회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<?> updateMember(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("memberSeq") Integer targetSeq,
      @Valid @RequestBody UpdateRequest req
  ) {
    // 1) 토큰 검사
    ResponseEntity<String> err = checkToken(authHeader);
    if (err != null) return err;

    // 2) 호출자, 대상 정보 조회
    Info me     = getMeFromToken(authHeader);
    Info target = svc.getMyInfoByMemberSeq(targetSeq);

    String myLevel    = me.getUserLevel();    // "0"=본사, "1"=지사, "2"=일반
    String tgtLevel   = target.getUserLevel();
    Integer myBranch  = me.getBranchSeq();
    Integer tgtBranch = target.getBranchSeq();

    // 3) 수정 권한 체크
    if ("2".equals(myLevel)) {
      // 일반 유저(userLevel=2): 수정 불가
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("권한이 없습니다.");
    }

    if ("1".equals(myLevel)) {
      // 1-1) 동일 지점인지 확인
      if (tgtBranch == null || !myBranch.equals(tgtBranch)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("같은 지점의 사용자만 수정할 수 있습니다.");
      }
      // 1-2) 대상은 “일반 유저” 또는 “본인”이어야 함
      boolean isSelf       = me.getMemberSeq().equals(targetSeq);
      boolean isGeneralUsr = "2".equals(tgtLevel);
      if (!(isSelf || isGeneralUsr)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("지사 관리자는 본인 또는 일반 유저만 수정할 수 있습니다.");
      }
      // 1-3) userLevel 변경은 불가
      if (req.getUserLevel() != null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("지사 관리자는 userLevel을 변경할 수 없습니다.");
      }
    }
    // else: myLevel == "0" (본사 관리자) → 제약 없음

    // 4) 권한 통과 후 실제 업데이트
    Info updated = svc.updateMemberInfo(
        targetSeq,
        req,
        me.getMemberSeq(),
        me.getUserId()
    );
    return ResponseEntity.ok(updated);
  }






  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{id}/status")
  public ResponseEntity<String> changeStatus(
      @PathVariable("id") Integer id,
      @Valid @RequestBody StatusChangeRequest req2,
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    ResponseEntity<String> err5 = checkToken(authHeader);
    if (err5 != null) return err5;
    Info me4 = getMeFromToken(authHeader);
    if (!"0".equals(me4.getUserLevel())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body("본사 관리자만 접근 가능합니다.");
    }
    svc.changeStatus(id, req2);
    return ResponseEntity.ok("상태 변경 완료");
  }
}
