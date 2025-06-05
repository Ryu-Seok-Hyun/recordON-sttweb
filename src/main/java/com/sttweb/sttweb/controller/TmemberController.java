package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.*;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
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
      return ResponseEntity.ok("가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청하신 권한(" + originalLevel +
          ") 대신 일반(2)로 처리되었습니다.");
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  /**
   * 로그인 엔드포인트 (클라이언트 IP 기준으로 지사 제한)
   *
   * - 본사(userLevel="0")인 경우 → IP 검증 없이 어디서든 로그인 허용
   *   + 단, 호스트 헤더(Host)가 지사 IP:Port와 매핑되면 해당 지사로 리다이렉트
   * - 지사 사용자(userLevel!="0")인 경우 →
   *     1) clientIp로 Branch 엔티티를 조회
   *     2) 조회된 Branch.branchSeq와 user.branchSeq가 같을 때만 로그인 허용
   *     3) 다르면 403 Forbidden
   */
  @LogActivity(type = "member", activity = "로그인")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @RequestBody LoginRequest req,
      HttpServletRequest request
  ) {
    // 1) 아이디/비밀번호 검증
    TmemberEntity user;
    try {
      user = svc.login(req);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 잘못되었습니다.");
    }

    // 2) Info DTO 변환 (MemberEntity → Info)
    Info info = Info.fromEntity(user);

    // 3) clientIp 얻기 (X-Forwarded-For 우선, 없으면 getRemoteAddr())
    String clientIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
        .filter(h -> !h.isBlank())
        .orElse(request.getRemoteAddr());

    // IPv6-IPv4 매핑 (::ffff:)
    if (clientIp != null && clientIp.startsWith("::ffff:")) {
      clientIp = clientIp.substring(7);
    }
    // IPv6 루프백(::1) → 127.0.0.1
    if ("::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp)) {
      clientIp = "127.0.0.1";
    }

    // 4) “127.0.0.1”인 경우, 실제로 DB에 등록된 지사 IP가 붙어 있는 NIC를 찾아 대체
    if ("127.0.0.1".equals(clientIp)) {
      String replaced = detectBranchIpFromLocalNics();
      if (replaced != null) {
        System.out.println("[DEBUG] loopback detected → 대체할 지사 IP = " + replaced);
        clientIp = replaced;
      } else {
        System.out.println("[DEBUG] loopback detected, 그러나 지사 IP로 매칭되는 NIC를 찾지 못함 → clientIp 그대로 127.0.0.1 유지");
      }
    }

    System.out.println("[DEBUG] 최종 clientIp = " + clientIp);

    // 5) 본사 여부 판단
    boolean isHqUser = "0".equals(info.getUserLevel());

    // “리다이렉트 대상 지사 IP/Port”를 결정하기 위한 변수들
    String resolvedBranchIp = null;   // 지사 IP가 채워지면 해당 지사 콘솔로 리다이렉트
    String resolvedBranchPort = null; // 지사 Port

    if (!isHqUser) {
      // 6) 지사 사용자인 경우, clientIp로 지사 조회 (p_ip 기준)
      Optional<TbranchEntity> reqBranchOpt = branchSvc.findBypIp(clientIp);
      if (reqBranchOpt.isEmpty()) {
        // p_ip 매칭 실패하면, 공인망 pb_ip로도 시도
        reqBranchOpt = branchSvc.findByPbIp(clientIp);
      }
      if (reqBranchOpt.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "접근할 수 있는 지사가 아닙니다. 요청 IP=" + clientIp
        );
      }
      TbranchEntity reqBranch = reqBranchOpt.get();

      System.out.println("[DEBUG] clientIp로 조회된 지사 → branchSeq="
          + reqBranch.getBranchSeq() + ", companyName=" + reqBranch.getCompanyName());

      // 7) “조회된 지사”와 “로그인 시도 사용자”의 branchSeq 비교
      Integer userBranchSeq = info.getBranchSeq();
      if (userBranchSeq == null || !userBranchSeq.equals(reqBranch.getBranchSeq())) {
        // 사용자 지사 이름 조회를 위해 TbranchEntity를 한 번 더 로드
        TbranchEntity userBranch = branchSvc.findEntityBySeq(userBranchSeq);
        String userBranchName = (userBranch != null)
            ? userBranch.getCompanyName()
            : "알 수 없음";

        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "해당 지사 사용자가 아닙니다. 사용자 지사="
                + userBranchName
                + ", 요청 지사="
                + reqBranch.getCompanyName()
        );
      }
      // 권한 검사 통과 → branchName 세팅
      info.setBranchName(reqBranch.getCompanyName());

      // 지사 사용자이므로, 해당 지사 IP/Port를 리다이렉트 대상으로 설정
      resolvedBranchIp   = reqBranch.getPIp();
      resolvedBranchPort = String.valueOf(reqBranch.getPPort());

    } else {
      // 본사인 경우, IP 검증 없이 바로 통과
      Integer userBranchSeq = info.getBranchSeq();
      if (userBranchSeq != null) {
        TbranchEntity branch = branchSvc.findEntityBySeq(userBranchSeq);
        if (branch != null) {
          info.setBranchName(branch.getCompanyName());
        }
      }

      // “호스트 헤더”를 검사하여, 만약 지사 IP:Port와 매핑되면 해당 지사로 전환
      String hostHeader = request.getHeader("Host");
      if (hostHeader != null) {
        // 예: "192.168.0.61:39080" 또는 "192.168.0.61"
        String[] parts = hostHeader.split(":");
        String headerIp   = parts[0].trim();
        Integer headerPort = (parts.length > 1)
            ? Integer.valueOf(parts[1].trim())
            : null;

        if (!headerIp.isBlank() && headerPort != null) {
          // 1) p_ip + p_port 매핑 조회
          Optional<TbranchEntity> br1 = branchSvc.findBypIp(headerIp)
              .filter(b -> b.getPPort().equals(headerPort));
          // 2) pb_ip + pb_port 매핑 조회
          Optional<TbranchEntity> br2 = branchSvc.findByPbIp(headerIp)
              .filter(b -> b.getPbPort().equals(headerPort));
          Optional<TbranchEntity> reqBranchOpt2 = (br1.isPresent() ? br1 : br2);

          if (reqBranchOpt2.isPresent()) {
            TbranchEntity reqBranch = reqBranchOpt2.get();
            info.setBranchName(reqBranch.getCompanyName());
            // 리다이렉트 대상 지사 IP/Port로 설정
            resolvedBranchIp   = reqBranch.getPIp();
            resolvedBranchPort = String.valueOf(reqBranch.getPPort());
          }
        }
      }
      // resolvedBranchIp가 null이라면 “본사 콘솔”(127.0.0.1:8080)으로 유지
      if (resolvedBranchIp == null) {
        info.setBranchName("본사");
      }
    }

    // 8) JWT 토큰 생성
    boolean isTempPassword = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTempPassword);

    String token = jwtTokenProvider.createTokenFromInfo(info);
    info.setToken(token);
    info.setTokenType("Bearer");

    // 9) Redirect URL 생성
    //    - 지사 사용자의 경우 resolvedBranchIp가 반드시 존재
    //    - 본사 사용자의 경우, resolvedBranchIp가 “호스트 헤더로 매핑된 지사”면 해당 지사, 아니면 본사 콘솔
    String hostForRedirect, portForRedirect;
    if (resolvedBranchIp != null) {
      hostForRedirect = resolvedBranchIp;
      portForRedirect = resolvedBranchPort;
    } else {
      hostForRedirect = "127.0.0.1";
      portForRedirect = "8080";
    }
    String redirectUrl = "http://" + hostForRedirect + ":" + portForRedirect + "?token=" + token;

    // 10) 응답을 Map이 아니라 DTO(LoginResponse)로 반환
    String message = null;
    if (isTempPassword) {
      message = "기본 비밀번호(1234)를 사용 중입니다. 로그인 후 반드시 변경하세요.";
    }

    LoginResponse loginRes = new LoginResponse(
        token,
        isHqUser,
        redirectUrl,
        message
    );

    return ResponseEntity.ok(loginRes);
  }

  /**
   * “127.0.0.1” 로 들어왔을 때, 서버에 붙어 있는 모든 NIC(IP) 중에서
   * DB에 등록된 지사의 p_ip 혹은 pb_ip로 사용 가능한 주소를 찾아 반환.
   * 없으면 null.
   */
  private String detectBranchIpFromLocalNics() {
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface ni = nics.nextElement();
        if (ni.isLoopback() || !ni.isUp()) continue;

        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress addr = addrs.nextElement();
          if (addr.isLoopbackAddress()) continue;

          String ip = addr.getHostAddress();
          if (!ip.contains(".")) continue;

          if (branchSvc.findBypIp(ip).isPresent()) {
            return ip;
          }
          if (branchSvc.findByPbIp(ip).isPresent()) {
            return ip;
          }
        }
      }
    } catch (SocketException e) {
      e.printStackTrace();
    }
    return null;
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

  /**
   * 10) 회원정보 종합 수정
   */
  @LogActivity(type = "member", activity = "수정", contents = "회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<Info> updateMember(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody UpdateRequest req
  ) {
    // 1) 로그인/재인증 체크
    Info me = requireLogin(authHeader);
    requireReAuth();

    // 2) 대상 사용자 정보 조회
    Info tgt = svc.getMyInfoByMemberSeq(memberSeq);
    String myLvl = me.getUserLevel();
    String tgLvl = tgt.getUserLevel();
    Integer myBs = me.getBranchSeq();
    Integer tgBs = tgt.getBranchSeq();

    // 3) 권한 검사:
    //    - userLevel=2(일반 유저) 는 수정 불가
    if ("2".equals(myLvl)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
    }

    //    - userLevel=1(지사 관리자) 은 같은 지사에 속한 사용자만 수정 가능,
    //      단 본인 수정과 일반(2)만 가능. (지사 관리자는 userLevel 변경 X, roleSeq 변경 X)
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
      // 지사 관리자는 userLevel 변경 불가
      if (req.getUserLevel() != null && !req.getUserLevel().equals(tgLvl)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 userLevel을 변경할 수 없습니다.");
      }
      // 지사 관리자는 roleSeq 변경 불가
      if (req.getRoleSeq() != null) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 roleSeq를 변경할 수 없습니다.");
      }
    }

    //    - userLevel=0(본사 관리자) 은 모든 사용자 수정 가능,
    //      단 roleSeq는 본사 관리자만 변경 허용 (따로 추가 조건 없음)

    // 4) 서비스 호출
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
