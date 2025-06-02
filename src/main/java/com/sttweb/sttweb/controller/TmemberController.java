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

  private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$";

  private final TmemberService svc;
  private final JwtTokenProvider jwtTokenProvider;
  private final TbranchService branchSvc;
  private final PasswordEncoder passwordEncoder;
  private final HttpServletRequest request;


  @Data
  public static class ReauthRequest { private String password; }

  @Data
  @AllArgsConstructor
  public static class ReauthResponse {
    private String reauthToken;
    private long expiresIn;
  }

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

  private Info getCurrentUserFromToken(String authHeader) {
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  private Info requireLogin(String authHeader) {
    return getCurrentUserFromToken(authHeader);
  }

  private void requireReAuth() {
    String reauth = request.getHeader("X-ReAuth-Token");
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    boolean ok = false;
    if (reauth != null && jwtTokenProvider.validateReAuthToken(reauth)) ok = true;
    else if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      if (jwtTokenProvider.validateToken(token)) ok = true;
    }
    if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "재인증이 필요합니다.");
  }

  @LogActivity(type = "member", activity = "등록", contents = "사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody SignupRequest req) {
    if (req.getUserPass() == null || !req.getUserPass().matches(PASSWORD_PATTERN)) {
      return ResponseEntity.badRequest().body("비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다.");
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
      return ResponseEntity.ok("가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청하신 권한(" + originalLevel + ") 대신 일반(2)로 처리되었습니다.");
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  /**
   *  로그인 엔드포인트
   *  POST /api/members/login
   */
  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @RequestBody LoginRequest req,
      HttpServletRequest request
  ) {
    // 1) 아이디/비밀번호 검증
    TmemberEntity user;
    try {
      user = svc.login(req);
    } catch (IllegalArgumentException e) {
      // 아이디/비번 불일치 시 401
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 잘못되었습니다.");
    }

    // 2) Info DTO 변환
    Info info = Info.fromEntity(user);

    // 3) 클라이언트 IP 정규화
    String clientIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
        .filter(h -> !h.isBlank())
        .orElse(request.getRemoteAddr());
    if (clientIp != null && clientIp.startsWith("::ffff:")) {
      clientIp = clientIp.substring(7);
    }
    if ("::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp)) {
      clientIp = "127.0.0.1";
    }

    // 4) 서버 호스트 정규화
    String serverHost = Optional.ofNullable(request.getHeader("Host"))
        .map(h -> h.split(":")[0])
        .orElse(request.getLocalAddr());
    if (serverHost != null && serverHost.startsWith("::ffff:")) {
      serverHost = serverHost.substring(7);
    }
    if ("::1".equals(serverHost) || "0:0:0:0:0:0:0:1".equals(serverHost)) {
      serverHost = "127.0.0.1";
    }

    // ────────────────────────────────────────────────────
    // 5) 디버깅용 로그
    System.out.println("[DEBUG] 요청 clientIp=" + clientIp + ", serverHost=" + serverHost);

    // 6) 사용자가 속한 지사(branchSeq) 조회
    Integer userBranchSeq = info.getBranchSeq();
    if (userBranchSeq == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소속 지사 정보가 없습니다.");
    }
    TbranchEntity branch = branchSvc.findEntityBySeq(userBranchSeq);
    if (branch == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DB에서 지사 정보를 찾을 수 없습니다.");
    }

    // ────────────────────────────────────────────────────
    // 7) 본사 유저인지 체크
    boolean isHqUser = "0".equals(info.getUserLevel());

    if (!isHqUser) {
      // ─ 지사 사용자라면 “127.0.0.1 예외 처리” 추가
      //    → 원격 데스크톱이나 로컬 테스트 목적: "localhost"로 접속된 경우 branchSeq만 보면 된다
      if (!"127.0.0.1".equals(clientIp) && !"127.0.0.1".equals(serverHost)) {
        // 클라이언트 IP가 127.0.0.1이 아니고, 서버 호스트도 127.0.0.1이 아니면
        // (즉, 실제 운영 환경 혹은 외부 접속 환경이라면) 아래 정상 IP 비교 로직 실행
        String branchPip   = branch.getPIp();   // 사설망 IP
        String branchPubip = branch.getPbIp();  // 공인망 IP

        System.out.println("[DEBUG] DB에 저장된 지사 사설망 IP=" + branchPip + ", 공인망 IP=" + branchPubip);

        boolean matchPrivClient = branchPip   != null && branchPip.equals(clientIp);
        boolean matchPubClient  = branchPubip != null && branchPubip.equals(clientIp);
        boolean matchPrivServer = branchPip   != null && branchPip.equals(serverHost);
        boolean matchPubServer  = branchPubip != null && branchPubip.equals(serverHost);

        if (!(matchPrivClient || matchPubClient || matchPrivServer || matchPubServer)) {
          throw new ResponseStatusException(
              HttpStatus.FORBIDDEN,
              "접근 권한이 없습니다. 요청 IP=" + clientIp +
                  ", 서버 호스트=" + serverHost +
                  " / 허용된 사설망 IP=" + branchPip +
                  ", 허용된 공인망 IP=" + branchPubip
          );
        }
      }
      // 만약 clientIp가 127.0.0.1 이거나 serverHost가 127.0.0.1 이라면
      // ⇒ 원격 데스크톱/로컬 테스트용으로 “branchSeq만 있으면 통과”하므로 추가 검사는 하지 않음
    }

    // ────────────────────────────────────────────────────
    // 8) JWT 생성 & 응답
    info.setBranchName(branch.getCompanyName());
    boolean isTempPassword = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTempPassword);

    String token = jwtTokenProvider.createTokenFromInfo(info);
    info.setToken(token);
    info.setTokenType("Bearer");

    String hostForRedirect = serverHost.equals(branch.getPIp())
        ? branch.getPIp() : branch.getPbIp();
    String portForRedirect = serverHost.equals(branch.getPIp())
        ? branch.getPPort() : branch.getPbPort();
    String redirectUrl = "http://" + hostForRedirect + ":" + portForRedirect + "?token=" + token;

    Map<String, Object> result = new HashMap<>();
    result.put("user", info);
    result.put("hqUser", isHqUser);
    if (isTempPassword) {
      result.put("message", "초기화된 비밀번호(1234)를 사용 중입니다. 로그인 후 반드시 비밀번호를 변경하세요.");
    }
    result.put("redirectUrl", redirectUrl);

    return ResponseEntity.ok(result);
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
      @RequestParam(name = "keyword",    required = false) String keyword,
      @RequestParam(name = "branchName", required = false) String branchName,
      @RequestParam(name = "page",       defaultValue = "0")  int page,
      @RequestParam(name = "size",       defaultValue = "10") int size
  ) {
    Info me  = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    // 본사 관리자(0)인 경우: branchName + keyword 조합 처리
    if ("0".equals(lvl)) {
      boolean hasKw = keyword    != null && !keyword.isBlank();
      boolean hasBn = branchName != null && !branchName.isBlank();
      Page<Info> p;

      if (hasBn && hasKw) {
        // 1) 지점명 + 키워드 조합 검색
        p = svc.searchUsersByBranchNameAndKeyword(
            branchName.trim(), keyword.trim(), pr
        );
      }
      else if (hasKw) {
        // 2) 키워드만
        p = svc.searchUsers(keyword.trim(), pr);
      }
      else if (hasBn) {
        // 3) 지점명만
        p = svc.searchUsersByBranchName(branchName.trim(), pr);
      }
      else {
        // 4) 전체 조회
        p = svc.listAllUsers(pr);
      }

      return ResponseEntity.ok(p);
    }

    // 지사 관리자(1)인 경우: 자신의 지사 내에서만 keyword 검색 또는 전체 조회
    if ("1".equals(lvl)) {
      Integer bs = me.getBranchSeq();
      if (bs == null) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body("내 지사 정보가 없습니다.");
      }
      Page<Info> p = (keyword != null && !keyword.isBlank())
          ? svc.searchUsersInBranch(keyword.trim(), bs, pr)
          : svc.listUsersInBranch(bs, pr);
      return ResponseEntity.ok(p);
    }

    // 일반 유저(2)인 경우: 자기 자신만
    Page<Info> self = new PageImpl<>(
        List.of(me),
        PageRequest.of(0, 1),
        1
    );
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
  @GetMapping("/{memberSeq:\\d+}")
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
