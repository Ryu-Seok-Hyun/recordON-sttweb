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
  public static class ReauthRequest {

    private String password;
  }

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
    if (reauth != null && jwtTokenProvider.validateReAuthToken(reauth))
      ok = true;
    else if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      if (jwtTokenProvider.validateToken(token))
        ok = true;
    }
    if (!ok)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "재인증이 필요합니다.");
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
    if ("1".equals(lvl))
      req.setUserLevel("2");
    svc.signupWithGrants(req, me.getMemberSeq(), me.getUserId());
    if (!originalLevel.equals(req.getUserLevel())) {
      return ResponseEntity.ok("가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청하신 권한(" + originalLevel +
          ") 대신 일반(2)로 처리되었습니다.");
    }
    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  /**
   * 로그인 엔드포인트 (클라이언트 IP 기준으로 지사 제한)
   * <p>
   * - 본사(userLevel="0")인 경우 → IP 검증 없이 어디서든 로그인 허용 + 단, 호스트 헤더(Host)가 지사 IP:Port와 매핑되면 해당 지사로
   * 리다이렉트 - 지사 사용자(userLevel!="0")인 경우 → 1) clientIp로 Branch 엔티티를 조회 2) 조회된 Branch.branchSeq와
   * user.branchSeq가 같을 때만 로그인 허용 3) 다르면 403 Forbidden
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

    // 2) DTO 변환 (회원 소속 branchSeq/branchName 은 여기서 세팅됨)
    Info info = Info.fromEntity(user);

    if (info.getBranchSeq() != null) {
      TbranchEntity orig = branchSvc.findEntityBySeq(info.getBranchSeq());
      if (orig != null) {
        info.setBranchName(orig.getCompanyName());
      }
    }

    // 3) clientIp 획득
    String clientIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
        .filter(h -> !h.isBlank())
        .orElse(request.getRemoteAddr());
    if (clientIp.startsWith("::ffff:"))
      clientIp = clientIp.substring(7);
    if ("::1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp))
      clientIp = "127.0.0.1";
    if ("127.0.0.1".equals(clientIp)) {
      String repl = detectBranchIpFromLocalNics();
      if (repl != null)
        clientIp = repl;
    }

    // 4) 본사 계정 여부 판단
    boolean isHqUser = "0".equals(info.getUserLevel());

    // —————— “콘솔 지점” 정보만 currentBranch 에 채워넣는 로직 ——————
    TbranchEntity reqBranch = null;
    String resolvedBranchIp = null;
    String resolvedBranchPort = null;

    // A) Body override
    if (req.getBranchSeq() != null) {
      TbranchEntity ov = branchSvc.findEntityBySeq(req.getBranchSeq());
      if (ov != null) {
        info.setCurrentBranchSeq(ov.getBranchSeq());
        info.setCurrentBranchName(ov.getCompanyName());
        resolvedBranchIp = ov.getPIp();
        resolvedBranchPort = String.valueOf(ov.getPPort());
        reqBranch = ov;
      }
    }

    // B) HQ + override 없을 때: 서버 NIC 로 자동 감지
    if (isHqUser && req.getBranchSeq() == null && resolvedBranchIp == null) {
      String localIp = request.getLocalAddr();
      Optional<TbranchEntity> auto = branchSvc.findBypIp(localIp)
          .or(() -> branchSvc.findByPbIp(localIp));
      if (auto.isPresent()) {
        TbranchEntity br = auto.get();
        info.setCurrentBranchSeq(br.getBranchSeq());
        info.setCurrentBranchName(br.getCompanyName());
        resolvedBranchIp = br.getPIp();
        resolvedBranchPort = String.valueOf(br.getPPort());
        reqBranch = br;
      }
    }

    // C) 지사 계정: clientIp 로 조회 후 덮어쓰기
    if (!isHqUser) {
      Optional<TbranchEntity> opt = branchSvc.findBypIp(clientIp);
      if (opt.isEmpty())
        opt = branchSvc.findByPbIp(clientIp);
      if (opt.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "접근할 수 있는 지사가 아닙니다. 요청 IP=" + clientIp);
      }
      reqBranch = opt.get();
      if (!info.getBranchSeq().equals(reqBranch.getBranchSeq())) {
        TbranchEntity ub = branchSvc.findEntityBySeq(info.getBranchSeq());
        String ubn = ub != null ? ub.getCompanyName() : "알 수 없음";
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "해당 지사 사용자가 아닙니다. 사용자 지사=" + ubn + ", 요청 지사=" + reqBranch.getCompanyName()
        );
      }
      info.setCurrentBranchSeq(reqBranch.getBranchSeq());
      info.setCurrentBranchName(reqBranch.getCompanyName());
      resolvedBranchIp = reqBranch.getPIp();
      resolvedBranchPort = String.valueOf(reqBranch.getPPort());
    }
    // D) 본사 + Host 헤더 매핑
    else {
      String hostH = request.getHeader("Host");
      if (hostH != null) {
        String[] parts = hostH.split(":");
        String hip = parts[0].trim();
        Integer hpt = parts.length > 1 ? Integer.valueOf(parts[1].trim()) : null;
        Optional<TbranchEntity> m1 = branchSvc.findBypIp(hip).filter(b -> b.getPPort().equals(hpt));
        Optional<TbranchEntity> m2 = branchSvc.findByPbIp(hip)
            .filter(b -> b.getPbPort().equals(hpt));
        Optional<TbranchEntity> mapped = m1.isPresent() ? m1 : m2;
        if (mapped.isPresent()) {
          reqBranch = mapped.get();
          info.setCurrentBranchSeq(reqBranch.getBranchSeq());
          info.setCurrentBranchName(reqBranch.getCompanyName());
          resolvedBranchIp = reqBranch.getPIp();
          resolvedBranchPort = String.valueOf(reqBranch.getPPort());
        }
      }
      if (resolvedBranchIp == null) {
        info.setCurrentBranchSeq(info.getBranchSeq());
        info.setCurrentBranchName(info.getBranchName());
      }
    }

    // 5) JWT 토큰 생성 준비
    boolean isTempPassword = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTempPassword);

    // 6) 최종 hq_yn 계산
    boolean finalHqYn = isHqUser && (reqBranch == null);
    info.setHqYn(finalHqYn);

    // 7) JWT 토큰 생성
    String token = jwtTokenProvider.createTokenFromInfo(info);
    info.setToken(token);
    info.setTokenType("Bearer");

    // 8) Redirect URL 생성
    String hostForRedirect = resolvedBranchIp != null ? resolvedBranchIp : "127.0.0.1";
    String portForRedirect = resolvedBranchPort != null ? resolvedBranchPort : "8080";
    String redirectUrl = "http://" + hostForRedirect + ":" + portForRedirect + "?token=" + token;

    // 9)  DTO(LoginResponse) 반환
    LoginResponse loginRes = new LoginResponse(
        token,
        finalHqYn,
        redirectUrl,
        isTempPassword ? "기본 비밀번호(1234)를 사용 중입니다. 로그인 후 변경하세요." : null,
        info.getCurrentBranchSeq(),
        info.getCurrentBranchName(),
        info.getBranchSeq(),
        info.getBranchName()
    );

    return ResponseEntity.ok(loginRes);
  }

  /**
     * “127.0.0.1” 로 들어왔을 때, 서버에 붙어 있는 모든 NIC(IP) 중에서
     * DB에 등록된 지사의 p_ip 혹은 pb_ip로 사용 가능한 주소를 찾아 반환.
     * 없으면 null.
     */
    private String detectBranchIpFromLocalNics () {
      try {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
          NetworkInterface ni = nics.nextElement();
          if (ni.isLoopback() || !ni.isUp())
            continue;

          Enumeration<InetAddress> addrs = ni.getInetAddresses();
          while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (addr.isLoopbackAddress())
              continue;

            String ip = addr.getHostAddress();
            if (!ip.contains("."))
              continue;

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

  /**
   * 14) 마스킹 여부 수정
   *    - 로그인된 사용자가 자신의 maskFlag(0=마스킹, 1=마스킹 해제) 값을 변경합니다.
   *    - 요청: PUT /api/members/mask
   *    - Body: { "maskFlag": 0 or 1 }
   */
  @Data
  public static class MaskFlagRequest {
    private Integer maskFlag;
  }

  @LogActivity(type = "member", activity = "수정", contents = "마스킹 여부 변경")
  @PutMapping("/mask")
  public ResponseEntity<String> updateMaskFlag(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody MaskFlagRequest req
  ) {
    // 1) 로그인된 회원 확인 → memberSeq 얻기
    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = svc.getMyInfoByUserId(userId);
    Integer memberSeq = me.getMemberSeq();

    // 2) 요청 값 검증: maskFlag는 0 또는 1이어야 함
    Integer newFlag = req.getMaskFlag();
    if (newFlag == null || (newFlag != 0 && newFlag != 1)) {
      return ResponseEntity.badRequest().body("maskFlag 값은 0(마스킹) 또는 1(마스킹 해제)이어야 합니다.");
    }

    // 3) 서비스 호출하여 DB 업데이트
    try {
      svc.updateMaskFlag(memberSeq, newFlag);
      String msg = (newFlag == 0)
          ? "마스킹이 활성화(번호 가운데 4자리 별표 처리)되었습니다."
          : "마스킹이 비활성화(번호 전체가 노출)되었습니다.";
      return ResponseEntity.ok(msg);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

}
