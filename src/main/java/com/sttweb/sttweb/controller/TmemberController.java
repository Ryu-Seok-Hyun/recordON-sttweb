// src/main/java/com/sttweb/sttweb/controller/TmemberController.java
package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.Info;
import com.sttweb.sttweb.dto.TmemberDto.LoginRequest;
import com.sttweb.sttweb.dto.TmemberDto.PasswordChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.SignupRequest;
import com.sttweb.sttweb.dto.TmemberDto.StatusChangeRequest;
import com.sttweb.sttweb.dto.TmemberDto.UpdateRequest;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class TmemberController {

  /** 비밀번호 정책: 최소 8자, 영어 소문자·숫자·특수문자 포함 */
  private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$";

  private final TmemberService svc;
  private final JwtTokenProvider jwtTokenProvider;
  private final TbranchService branchSvc;
  private final PasswordEncoder passwordEncoder;

  /** Authorization 헤더에서 "Bearer " 제거 및 검증 */
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

  /** 토큰에서 userId 꺼내서 내 정보 조회 */
  private Info getCurrentUserFromToken(String authHeader) {
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  /** 로그인된 사용자만 허용 (토큰 검증) */
  private Info requireLogin(String authHeader) {
    return getCurrentUserFromToken(authHeader);
  }

  @LogActivity(type = "member", activity = "등록", contents = "사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody SignupRequest req
  ) {
    if (req.getUserPass() == null || !req.getUserPass().matches(PASSWORD_PATTERN)) {
      return ResponseEntity.badRequest()
          .body("비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다.");
    }
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");
    }
    if (svc.existsByUserId(req.getUserId())) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 ID 입니다.");
    }
    String originalLevel = req.getUserLevel();
    if ("1".equals(lvl)) req.setUserLevel("2");
    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());
    if (!originalLevel.equals(req.getUserLevel())) {
      return ResponseEntity.ok("가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청하신 권한(" +
          originalLevel + ") 대신 일반(2)로 처리되었습니다.");
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody LoginRequest req,
      HttpServletRequest request
  ) {
    // 1) 클라이언트 IP 획득
    String rawIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
        .filter(h -> !h.isBlank())
        .orElse(request.getRemoteAddr());

    if ("::1".equals(rawIp) || "0:0:0:0:0:0:0:1".equals(rawIp)) {
      rawIp = "127.0.0.1";
    }
    final String lookupIp = rawIp;

    // 2) 지사 조회 (개발환경에서는 실패해도 무시)
    TbranchEntity branch = null;
    try {
      branch = branchSvc.findBypIp(lookupIp)
          .orElseThrow(() ->
              new IllegalArgumentException("해당 IP에 해당하는 지사가 없습니다: " + lookupIp)
          );
    } catch (IllegalArgumentException ignored) {
    }

    // 3) 인증
    TmemberEntity user = svc.login(req);

    // 4) JWT 생성
    String token = jwtTokenProvider.createToken(
        user.getUserId(), user.getUserLevel(), user.getBranchSeq());

    // 5) DTO 구성
    Info info = Info.fromEntity(user);
    info.setToken(token);
    info.setTokenType("Bearer");
    if (branch != null) {
      info.setBranchName(branch.getCompanyName());
    }

    // 6) 임시 비밀번호 사용 중인지 판단
    boolean isTemp = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTemp);

    // 7) 응답 맵 구성
    Map<String, Object> res = new HashMap<>();
    res.put("user", info);

    // 8) 임시 비밀번호 상태 메시지 추가
    if (isTemp) {
      res.put("message", "초기화 된 비밀번호(1234)를 사용 중입니다. 로그인 후 반드시 비밀번호를 변경하세요.");
    }

    // 9) 선택적 리다이렉트 URL
    if (branch != null && branch.getPIp() != null && branch.getPPort() != null) {
      String redirectUrl = "http://" + branch.getPIp()
          + ":" + branch.getPPort()
          + "?token=" + token;
      res.put("redirectUrl", redirectUrl);
    }

    return ResponseEntity.ok(res);
  }

  /**
   * 내 비밀번호 변경
   * PUT /api/members/password
   * Body: { "oldPassword": "...", "newPassword": "..." }
   */
  @LogActivity(type = "member", activity = "비밀번호 변경", contents = "내 비밀번호 변경")
  @PutMapping("/password")
  public ResponseEntity<String> changeMyPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody PasswordChangeRequest req
  ) {
    // 1) 로그인 검사
    Info me = requireLogin(authHeader);
    // 2) 서비스 호출
    svc.changePassword(me.getMemberSeq(), req.getOldPassword(), req.getNewPassword());
    // 3) 응답
    return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
  }


  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "단일 초기화")
  @PutMapping("/{memberSeq}/changpass")
  public ResponseEntity<String> resetPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable Integer memberSeq
  ) {
    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetPassword(memberSeq, "1234", me.getUserId());
    return ResponseEntity.ok("사용자 " + memberSeq + " 비밀번호가 초기화되었습니다. 기본(1234)로 로그인 후 변경하세요.");
  }

  // (1) 여러 명 동시 초기화
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "여러 명 동시 초기화")
  @PutMapping("/changpass/bulk")
  public ResponseEntity<String> resetPasswordsBulk(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody List<Integer> memberSeqs
  ) {
    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetPasswords(memberSeqs, "1234", me.getUserId());
    return ResponseEntity.ok(
        "사용자 " + memberSeqs + "의 비밀번호가 모두 초기화되었습니다. 기본(1234)로 로그인 후 변경하세요."
    );
  }

  // (2) 전체 초기화
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "전체 사용자 초기화")
  @PutMapping("/changpass/all")
  public ResponseEntity<String> resetAllPasswords(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
  ) {
    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetAllPasswords("1234", me.getUserId());
    return ResponseEntity.ok(
        "전체 사용자 비밀번호가 초기화되었습니다. 기본(1234)로 로그인 후 변경하세요."
    );
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
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    return ResponseEntity.ok(requireLogin(authHeader));
  }

  @LogActivity(type = "member", activity = "조회", contents = "전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<?> listOrSearchUsers(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);
    if ("0".equals(lvl)) {
      Page<Info> p = (keyword != null && !keyword.isBlank())
          ? svc.searchUsers(keyword.trim(), pr)
          : svc.listAllUsers(pr);
      return ResponseEntity.ok(p);
    }
    if ("1".equals(lvl)) {
      Integer bs = me.getBranchSeq();
      if (bs == null) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("내 지사 정보가 없습니다.");
      }
      Page<Info> p = (keyword != null && !keyword.isBlank())
          ? svc.searchUsersInBranch(keyword.trim(), bs, pr)
          : svc.listUsersInBranch(bs, pr);
      return ResponseEntity.ok(p);
    }
    Page<Info> self = new org.springframework.data.domain.PageImpl<>(
        java.util.List.of(me), pr, 1
    );
    return ResponseEntity.ok(self);
  }

  @LogActivity(type = "member", activity = "수정", contents = "회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<Info> updateMember(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody UpdateRequest req
  ) {
    Info me = requireLogin(authHeader);
    Info tgt = svc.getMyInfoByMemberSeq(memberSeq);
    String myLvl = me.getUserLevel(), tgLvl = tgt.getUserLevel();
    Integer myBs = me.getBranchSeq(), tgBs = tgt.getBranchSeq();
    if ("2".equals(myLvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    if ("1".equals(myLvl)) {
      if (tgBs == null || !myBs.equals(tgBs)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "같은 지점의 사용자만 수정 가능합니다.");
      }
      boolean self = me.getMemberSeq().equals(memberSeq);
      boolean gen = "2".equals(tgLvl);
      if (!(self || gen)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지사 관리자는 본인 또는 일반유저만 수정 가능합니다.");
      }
      if (req.getUserLevel() != null && !req.getUserLevel().equals(tgLvl)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지사 관리자는 userLevel을 변경할 수 없습니다.");
      }
    }
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
    if (!"0".equals(me.getUserLevel()) && !"1".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }
    return ResponseEntity.ok(svc.getInfoByMemberSeq(memberSeq));
  }

  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{memberSeq}/status")
  public ResponseEntity<String> changeStatus(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody StatusChangeRequest req
  ) {
    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.changeStatus(memberSeq, req);
    return ResponseEntity.ok("상태 변경 완료");
  }
}
