package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.*;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

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
  private final HttpServletRequest request;

  /** 재인증 요청 DTO */
  @Data
  public static class ReauthRequest { private String password; }

  /** 재인증 응답 DTO */
  @Data @AllArgsConstructor
  public static class ReauthResponse {
    private String reauthToken;
    private long expiresIn;  // 초 단위
  }

  /** "Bearer " 제거 + JWT 유효성 검사 */
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

  /** 토큰에서 userId 추출 후 내 정보 조회 */
  private Info getCurrentUserFromToken(String authHeader) {
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  /** 로그인된 사용자만 허용 **/
  private Info requireLogin(String authHeader) {
    return getCurrentUserFromToken(authHeader);
  }

  /** 민감 작업 전: X-ReAuth-Token 헤더 검사 **/
  private void requireReAuth() {
    String reauth = request.getHeader("X-ReAuth-Token");
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    boolean ok = false;
    // 1) 재인증 토큰이 VALID 한 경우
    if (reauth != null && jwtTokenProvider.validateReAuthToken(reauth)) {
      ok = true;
    }
    // 2) 아니면 로그인 토큰이 VALID 한 경우
    else if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      if (jwtTokenProvider.validateToken(token)) {
        ok = true;
      }
    }

    if (!ok) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "재인증이 필요합니다.");
    }
  }

  // ---------------------------------------
  // 1) 회원가입
  // ---------------------------------------
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
    if ("1".equals(lvl)) {
      req.setUserLevel("2"); // 지사 관리자는 일반(2)만
    }
    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());
    if (!originalLevel.equals(req.getUserLevel())) {
      return ResponseEntity.ok("가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청하신 권한(" +
          originalLevel + ") 대신 일반(2)로 처리되었습니다.");
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  // ---------------------------------------
  // 2) 로그인
  // ---------------------------------------
  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody LoginRequest req,
      HttpServletRequest request
  ) {
    String rawIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
        .filter(h -> !h.isBlank())
        .orElse(request.getRemoteAddr());
    final String lookupIp = ("::1".equals(rawIp) || "0:0:0:0:0:0:0:1".equals(rawIp))
        ? "127.0.0.1"
        : rawIp;

    TbranchEntity branch = null;
    try {
      branch = branchSvc.findBypIp(lookupIp)
          .orElseThrow(() ->
              new IllegalArgumentException("해당 IP에 해당하는 지사가 없습니다: " + lookupIp)
          );
    } catch (IllegalArgumentException ignored) {}

    TmemberEntity user = svc.login(req);
    Info info = Info.fromEntity(user);
    if (branch != null) {
      info.setBranchName(branch.getCompanyName());
    }
    boolean isTemp = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTemp);

    String token = jwtTokenProvider.createTokenFromInfo(info);
    info.setToken(token);
    info.setTokenType("Bearer");

    Map<String, Object> res = new HashMap<>();
    res.put("user", info);
    if (isTemp) {
      res.put("message", "초기화 된 비밀번호(1234)를 사용 중입니다. 로그인 후 반드시 비밀번호를 변경하세요.");
    }
    if (branch != null && branch.getPIp() != null && branch.getPPort() != null) {
      String redirectUrl = "http://" + branch.getPIp()
          + ":" + branch.getPPort()
          + "?token=" + token;
      res.put("redirectUrl", redirectUrl);
    }
    return ResponseEntity.ok(res);
  }

  // ---------------------------------------
  // 3) 내 비밀번호 변경
  // ---------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 변경", contents = "내 비밀번호 변경")
  @PutMapping("/password")
  public ResponseEntity<String> changeMyPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody PasswordChangeRequest req
  ) {
    Info me = requireLogin(authHeader);
    svc.changePassword(me.getMemberSeq(), req.getOldPassword(), req.getNewPassword());
    return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
  }

  // ---------------------------------------
  // 4) 단일 비밀번호 초기화 (민감)
  // ---------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "단일 초기화")
  @PutMapping("/{memberSeq}/changpass")
  public ResponseEntity<String> resetPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable Integer memberSeq
  ) {
    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetPassword(memberSeq, "1234", me.getUserId());
    return ResponseEntity.ok(
        "사용자 " + memberSeq + " 비밀번호가 초기화되었습니다. 기본(1234)로 로그인 후 변경하세요."
    );
  }

  // ---------------------------------------
  // 5) 여러 명 동시 초기화 (민감)
  // ---------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "여러 명 동시 초기화")
  @PutMapping("/changpass/bulk")
  public ResponseEntity<String> resetPasswordsBulk(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody List<Integer> memberSeqs
  ) {
    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetPasswords(memberSeqs, "1234", me.getUserId());
    return ResponseEntity.ok("사용자 " + memberSeqs + "의 비밀번호가 모두 초기화되었습니다.");
  }

  // ---------------------------------------
  // 6) 전체 초기화 (민감)
  // ---------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "전체 사용자 초기화")
  @PutMapping("/changpass/all")
  public ResponseEntity<String> resetAllPasswords(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
  ) {
    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.resetAllPasswords("1234", me.getUserId());
    return ResponseEntity.ok("전체 사용자 비밀번호가 초기화되었습니다.");
  }

  // ---------------------------------------
  // 7) 로그아웃
  // ---------------------------------------
  @LogActivity(type = "member", activity = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<String> logout() {
    svc.logout();
    return ResponseEntity.ok("로그아웃 완료");
  }

  // ---------------------------------------
  // 8) 내 정보 조회
  // ---------------------------------------
  @LogActivity(type = "member", activity = "조회", contents = "내 정보 조회")
  @GetMapping("/me")
  public ResponseEntity<Info> getMyInfo(
      @RequestHeader(value = "Authorization", required = false) String authHeader
  ) {
    return ResponseEntity.ok(requireLogin(authHeader));
  }

  // ---------------------------------------
  // 9) 전체/검색 조회
  // ---------------------------------------
  @LogActivity(type = "member", activity = "조회", contents = "전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<?> listOrSearchUsers(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "branchName", required = false) String branchName,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size
  ) {
    Info me = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    if ("0".equals(lvl)) {
      Page<Info> p;
      if (keyword != null && !keyword.isBlank()) {
        p = svc.searchUsers(keyword.trim(), pr);
      } else if (branchName != null && !branchName.isBlank()) {
        p = svc.searchUsersByBranchName(branchName.trim(), pr);
      } else {
        p = svc.listAllUsers(pr);
      }
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

    Page<Info> self = new PageImpl<>(List.of(me), PageRequest.of(0, 1), 1);
    return ResponseEntity.ok(self);
  }

  // ---------------------------------------
  // 10) 회원정보 종합 수정
  // ---------------------------------------
  @LogActivity(type = "member", activity = "수정", contents = "회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<Info> updateMember(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody UpdateRequest req
  ) {
    Info me = requireLogin(authHeader);
    requireReAuth();
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 본인 또는 일반유저만 수정 가능합니다.");
      }
      if (req.getUserLevel() != null && !req.getUserLevel().equals(tgLvl)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 userLevel을 변경할 수 없습니다.");
      }
    }
    Info updated = svc.updateMemberInfo(memberSeq, req, me.getMemberSeq(), me.getUserId());
    return ResponseEntity.ok(updated);
  }

  // ---------------------------------------
  // 11) 회원 상세조회
  // ---------------------------------------
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

  // ---------------------------------------
  // 12) 상태 변경 (민감)
  // ---------------------------------------
  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{memberSeq}/status")
  public ResponseEntity<String> changeStatus(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody StatusChangeRequest req
  ) {
    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!"0".equals(me.getUserLevel())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");
    }
    svc.changeStatus(memberSeq, req);
    return ResponseEntity.ok("상태 변경 완료");
  }

  // ---------------------------------------
  // 13) 관리자 재인증 (비밀번호 확인)
  // ---------------------------------------
  @LogActivity(type = "member", activity = "재인증", contents = "관리자 재인증")
  @PostMapping("/confirm-password")
  public ResponseEntity<ReauthResponse> confirmPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody ReauthRequest req
  ) {
    Info me = getCurrentUserFromToken(authHeader);
    TmemberEntity user = svc.findEntityByUserId(me.getUserId());
    if (!passwordEncoder.matches(req.getPassword(), user.getUserPass())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비밀번호가 틀렸습니다.");
    }
    String token = jwtTokenProvider.createReAuthToken(me.getUserId());
    long expiresIn = Duration.ofMinutes(30).getSeconds();
    return ResponseEntity.ok(new ReauthResponse(token, expiresIn));
  }
}
