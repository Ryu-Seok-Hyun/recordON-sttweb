// src/main/java/com/sttweb/sttweb/controller/TmemberController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto;
import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  private final TmemberService svc;                       // ← 하나만 남김
  private final JwtTokenProvider jwtTokenProvider;

  // ← 추가됨: Authorization 헤더에서 "Bearer " 떼고 유효성 검사까지
  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");
    }
    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
    return token;
  }

  // ← 추가됨: 토큰에서 사용자 정보 꺼내는 유틸
  private Info getCurrentUserFromToken(String authHeader) {
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  // ← 추가됨: 로그인만 되어 있으면 OK
  private Info requireLogin(String authHeader) {
    return getCurrentUserFromToken(authHeader);
  }

  @LogActivity(type="member", activity="등록", contents="사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @Valid @RequestBody SignupRequest req
  ) {
    // 본사(0) 또는 지사(1) 관리자만
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");
    }

    // 중복 검사
    if (svc.existsByUserId(req.getUserId())) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 ID 입니다.");
    }

    // 지사 관리자는 무조건 일반유저(2)로 내리기
    String originalLevel = req.getUserLevel();
    if ("1".equals(me.getUserLevel())) {
      req.setUserLevel("2");
    }

    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());

    if (!originalLevel.equals(req.getUserLevel())) {
      return ResponseEntity.ok(
          "가입 완료. 지사 관리자는 일반유저(2)만 생성할 수 있어, 요청하신 권한("
              + originalLevel + ") 대신 일반유저(2)로 처리되었습니다."
      );
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<Info> login(@Valid @RequestBody LoginRequest req) {
    TmemberEntity user = svc.login(req);
    String token = jwtTokenProvider.createToken(user.getUserId(), user.getUserLevel());
    Info info = Info.fromEntity(user);
    if (info.getBranchSeq() != null) {
      info.setBranchName(svc.getMyInfoByUserId(user.getUserId()).getBranchName());
    }
    info.setTokenType("Bearer");
    info.setToken(token);
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
  public ResponseEntity<Info> getMyInfo(
      @RequestHeader(value="Authorization", required=false) String authHeader
  ) {
    Info me = requireLogin(authHeader);
    return ResponseEntity.ok(me);
  }

  @LogActivity(type="member", activity="조회", contents="전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<?> listOrSearchUsers(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @RequestParam(name="keyword", required=false) String keyword,
      @RequestParam(name="page", defaultValue="0") int page,
      @RequestParam(name="size", defaultValue="10") int size
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    if ("0".equals(lvl)) {
      Page<Info> result = (keyword != null && !keyword.isBlank())
          ? svc.searchUsers(keyword.trim(), pr)
          : svc.listAllUsers(pr);
      return ResponseEntity.ok(result);

    } else if ("1".equals(lvl)) {
      Integer myBranch = me.getBranchSeq();
      if (myBranch == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("내 지사 정보가 없습니다.");
      }
      Page<Info> result = (keyword != null && !keyword.isBlank())
          ? svc.searchUsersInBranch(keyword.trim(), myBranch, pr)
          : svc.listUsersInBranch(myBranch, pr);
      return ResponseEntity.ok(result);

    } else if ("2".equals(lvl)) {
      // 일반유저: 본인 정보만
      Page<Info> pageOfOne = new org.springframework.data.domain.PageImpl<>(
          java.util.List.of(me), pr, 1
      );
      return ResponseEntity.ok(pageOfOne);

    } else {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
    }
  }

  @LogActivity(type="member", activity="수정", contents="회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<Info> updateMember(
      @RequestHeader(value="Authorization", required=false) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @Valid @RequestBody UpdateRequest req
  ) {
    Info me     = requireLogin(authHeader);
    Info target = svc.getMyInfoByMemberSeq(memberSeq);

    String myLevel    = me.getUserLevel();    // "0"=본사, "1"=지사, "2"=일반
    String tgtLevel   = target.getUserLevel();
    Integer myBranch  = me.getBranchSeq();
    Integer tgtBranch = target.getBranchSeq();

    // 일반유저(2)는 수정 불가
    if ("2".equals(myLevel)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    // 지사관리자(1) 제약
    if ("1".equals(myLevel)) {
      // 1) 동일 지점인지 체크
      if (tgtBranch == null || !myBranch.equals(tgtBranch)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "같은 지점의 사용자만 수정 가능합니다.");
      }
      // 2) 대상은 “본인” 또는 “일반유저(2)”이어야 함
      boolean isSelf    = me.getMemberSeq().equals(memberSeq);
      boolean isGeneral = "2".equals(tgtLevel);
      if (!(isSelf || isGeneral)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지사 관리자는 본인 또는 일반유저만 수정 가능합니다.");
      }
      // 3) userLevel 변경 시도인데, 값이 실제로 다르면 차단
      if (req.getUserLevel() != null
          && !req.getUserLevel().equals(tgtLevel)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지사 관리자는 userLevel을 변경할 수 없습니다.");
      }
      // → req.getUserLevel()==tgtLevel인 경우(재전송)에는 그대로 통과됩니다.
    }

    // 본사 관리자("0")는 제약 없음

    Info updated = svc.updateMemberInfo(
        memberSeq,
        req,
        me.getMemberSeq(),
        me.getUserId()
    );
    return ResponseEntity.ok(updated);
  }


  @LogActivity(type = "member", activity = "조회", contents = "회원 상세조회")
  @GetMapping("/{memberSeq}")
  public ResponseEntity<Info> getMemberDetail(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    Info target = svc.getInfoByMemberSeq(memberSeq);
    return ResponseEntity.ok(target);
  }

  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{memberSeq}/status")
  public ResponseEntity<String> changeStatus(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @Valid @RequestBody StatusChangeRequest req
  ) {
    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.changeStatus(memberSeq, req);
    return ResponseEntity.ok("상태 변경 완료");
  }
}
