package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.dto.TmemberDto.*;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.dto.GrantDto;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import com.sttweb.sttweb.logging.LogActivity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import com.sttweb.sttweb.service.PermissionService;
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

  // -----------------------------------------------------------------
  // 상수/DI
  // -----------------------------------------------------------------
  private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*\\d)(?=.*\\W).{8,}$";

  // ★ 추가: DB(tbranch.ip_type)에서 HQ 전용 지사를 식별하기 위한 값
  private static final int HQ_ONLY_TYPE = 1;

  private final TmemberService    svc;
  private final PermissionService permSvc;
  private final JwtTokenProvider  jwtTokenProvider;
  private final TbranchService    branchSvc;
  private final PasswordEncoder   passwordEncoder;
  private final HttpServletRequest request;

  // -----------------------------------------------------------------
  // DTO 내부 클래스
  // -----------------------------------------------------------------
  @Data
  public static class ReauthRequest { private String password; }

  @Data
  @AllArgsConstructor
  public static class ReauthResponse {
    private String reauthToken;
    private long   expiresIn;
  }

  @Data
  public static class MaskFlagRequest { private Integer maskFlag; }

  // -----------------------------------------------------------------
  // 공통 유틸
  // -----------------------------------------------------------------
  private String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer "))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 없습니다.");

    String token = authHeader.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");

    return token;
  }

  private Info getCurrentUserFromToken(String authHeader) {
    String token  = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    return svc.getMyInfoByUserId(userId);
  }

  private Info requireLogin(String authHeader) { return getCurrentUserFromToken(authHeader); }

  private void requireReAuth() {
    String reauth     = request.getHeader("X-ReAuth-Token");
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    boolean ok = false;

    if (reauth != null && jwtTokenProvider.validateReAuthToken(reauth)) ok = true;
    else if (authHeader != null && authHeader.startsWith("Bearer "))
      ok = jwtTokenProvider.validateToken(authHeader.substring(7).trim());

    if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "재인증이 필요합니다.");
  }

  // -----------------------------------------------------------------
  // 회원가입
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "등록", contents = "사용자 등록")
  @PostMapping("/signup")
  public ResponseEntity<String> signup(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody SignupRequest req) {

    if (req.getUserPass() == null || !req.getUserPass().matches(PASSWORD_PATTERN))
      return ResponseEntity.badRequest()
          .body("비밀번호는 최소 8자 이상이며, 영어 소문자·숫자·특수문자를 포함해야 합니다.");

    Info me  = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    if (!"0".equals(lvl) && !"1".equals(lvl))
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자만 접근 가능합니다.");

    if (svc.existsByUserId(req.getUserId()))
      return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 ID 입니다.");

    String origLevel = req.getUserLevel();
    if ("1".equals(lvl)) req.setUserLevel("2");

    // 1) 사용자 등록
    svc.signup(req, me.getMemberSeq(), me.getUserId());

    // 2) 권한 부여(grant)
    Integer newMemberSeq = svc.getMemberSeqByUserId(req.getUserId());
    if (req.getGrants() != null && !req.getGrants().isEmpty()) {
      for (GrantDto g : req.getGrants()) {
        g.setMemberSeq(newMemberSeq);
        permSvc.grantAndSyncLinePerm(g);
      }
    }

    if (!origLevel.equals(req.getUserLevel()))
      return ResponseEntity.ok(
          "가입 완료. 지사 관리자는 일반(2)만 생성할 수 있어, 요청 권한("
              + origLevel + ") 대신 일반(2)로 처리했습니다.");

    return ResponseEntity.ok("가입 및 권한부여 완료");
  }

  // ================================================================
// ★ 로그인 로직 전체 (오류 수정)
// ================================================================
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
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "아이디 또는 비밀번호가 잘못되었습니다."
      );
    }

    // 2) “본사(hqYn=0) 아니면, 자기 지사 서버여야” 검사
    TbranchEntity home = branchSvc.findEntityBySeq(user.getBranchSeq());
    boolean isHqUser = home != null && "0".equals(home.getHqYn());

    // 서버 IP/Port 결정 (detectBranchIpFromLocalNics() + fallback)
    String nicIp = detectBranchIpFromLocalNics();
    final String serverIp = (nicIp != null)
        ? nicIp
        : Optional.ofNullable(request.getHeader("Host"))
            .filter(h -> h.contains(":"))
            .map(h -> h.split(":")[0])
            .orElse(request.getLocalAddr());
    final String serverPortStr = String.valueOf(request.getServerPort());

    System.out.printf("[DEBUG][LOGIN] 서버IP=%s, 서버Port=%s%n", serverIp, serverPortStr);

    Optional<TbranchEntity> srvBr = branchSvc.findBypIp(serverIp)
        .filter(b -> serverPortStr.equals(b.getPPort()))
        .or(() -> branchSvc.findByPbIp(serverIp)
            .filter(b -> serverPortStr.equals(b.getPbPort()))
        );

    if (!isHqUser) {
      // 지사 계정인데, 접속 서버가 없거나 내 지사와 다르면 차단
      if (srvBr.isEmpty() || !srvBr.get().getBranchSeq().equals(home.getBranchSeq())) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "해당 지사 서버에서는 본사 또는 해당 지사 사용자만 로그인할 수 있습니다."
        );
      }
    }

    // 3) JWT 생성 이하 로직 (기존 그대로)
    Info info = Info.fromEntity(user);
    if (home != null) info.setBranchName(home.getCompanyName());

    // “현재 접속 지사” 세팅
    if (srvBr.isPresent()) {
      info.setCurrentBranchSeq(srvBr.get().getBranchSeq());
      info.setCurrentBranchName(srvBr.get().getCompanyName());
    } else {
      info.setCurrentBranchSeq(info.getBranchSeq());
      info.setCurrentBranchName(info.getBranchName());
    }

    boolean isTempPassword = passwordEncoder.matches("1234", user.getUserPass());
    info.setMustChangePassword(isTempPassword);

    boolean finalHqYn = isHqUser && srvBr.isEmpty();
    info.setHqYn(finalHqYn);

    String token = jwtTokenProvider.createTokenFromInfo(info);
    info.setToken(token);
    info.setTokenType("Bearer");

    String redirectHost = srvBr.isPresent() ? serverIp : "127.0.0.1";
    String redirectPort = srvBr.isPresent()
        ? serverPortStr
        : "8080";
    String redirectUrl = "http://" + redirectHost + ":" + redirectPort + "?token=" + token;

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



  // -----------------------------------------------------------------
  // Host 헤더 → IP 추출 헬퍼
  // -----------------------------------------------------------------
  // ★ 추가
  private String resolveServerIp(HttpServletRequest req) {
    String host = req.getHeader("Host");
    if (host != null && !host.isBlank())
      return host.split(":")[0].trim();
    return req.getLocalAddr();
  }

  // -----------------------------------------------------------------
  // 127.0.0.1 보정용 NIC 탐색
  // -----------------------------------------------------------------
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

          if (branchSvc.findBypIp(ip).isPresent()) return ip;
          if (branchSvc.findByPbIp(ip).isPresent()) return ip;
        }
      }
    } catch (SocketException e) {
      e.printStackTrace();
    }
    return null;
  }

  // -----------------------------------------------------------------
  // 비밀번호 변경
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 변경", contents = "내 비밀번호 변경")
  @PutMapping("/password")
  public ResponseEntity<String> changeMyPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody PasswordChangeRequest req) {

    Info me = requireLogin(authHeader);
    svc.changePassword(me.getMemberSeq(), req.getOldPassword(), req.getNewPassword());
    return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
  }

  // -----------------------------------------------------------------
  // 단일 비밀번호 초기화
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "단일 초기화")
  @PutMapping("/{memberSeq}/changpass")
  public ResponseEntity<String> resetPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable Integer memberSeq) {

    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!("0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 및 지사 관리자만 접근 가능합니다.");

    svc.resetPassword(memberSeq, "1234", me.getUserId());
    return ResponseEntity.ok(
        "사용자 " + memberSeq + " 비밀번호가 초기화되었습니다. 기본(1234)로 로그인 후 변경하세요."
    );
  }

  // -----------------------------------------------------------------
  // 여러 명 비밀번호 초기화
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "여러 명 동시 초기화")
  @PutMapping("/changpass/bulk")
  public ResponseEntity<String> resetPasswordsBulk(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody List<Integer> memberSeqs) {

    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!("0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 및 지사 관리자만 접근 가능합니다.");

    svc.resetPasswords(memberSeqs, "1234", me.getUserId());
    return ResponseEntity.ok("사용자 " + memberSeqs + "의 비밀번호가 모두 초기화되었습니다.");
  }

  // -----------------------------------------------------------------
  // 전체 비밀번호 초기화
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "비밀번호 초기화", contents = "전체 사용자 초기화")
  @PutMapping("/changpass/all")
  public ResponseEntity<String> resetAllPasswords(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!("0".equals(me.getUserLevel()) || "1".equals(me.getUserLevel())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 및 지사 관리자만 접근 가능합니다.");

    svc.resetAllPasswords("1234", me.getUserId());
    return ResponseEntity.ok("전체 사용자 비밀번호가 초기화되었습니다.");
  }

  // -----------------------------------------------------------------
  // 로그아웃
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<String> logout() {
    svc.logout();
    return ResponseEntity.ok("로그아웃 완료");
  }

  // -----------------------------------------------------------------
  // 내 정보 조회
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "조회", contents = "내 정보 조회")
  @GetMapping("/me")
  public ResponseEntity<Info> getMyInfo(
      @RequestHeader(value = "Authorization", required = false) String authHeader) {

    return ResponseEntity.ok(requireLogin(authHeader));
  }

  // -----------------------------------------------------------------
  // 전체/검색 조회
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "조회", contents = "전체 유저 조회/검색")
  @GetMapping
  public ResponseEntity<?> listOrSearchUsers(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam(name = "keyword",    required = false) String keyword,
      @RequestParam(name = "branchName", required = false) String branchName,
      @RequestParam(name = "page",       defaultValue = "0")  int page,
      @RequestParam(name = "size",       defaultValue = "10") int size) {

    Info me  = requireLogin(authHeader);
    String lvl = me.getUserLevel();
    Pageable pr = PageRequest.of(page, size);

    if ("0".equals(lvl)) { // 본사 관리자
      boolean hasKw = keyword    != null && !keyword.isBlank();
      boolean hasBn = branchName != null && !branchName.isBlank();
      Page<Info> p;

      if (hasBn && hasKw) {
        p = svc.searchUsersByBranchNameAndKeyword(branchName.trim(), keyword.trim(), pr);
      } else if (hasKw) {
        p = svc.searchUsers(keyword.trim(), pr);
      } else if (hasBn) {
        p = svc.searchUsersByBranchName(branchName.trim(), pr);
      } else {
        p = svc.listAllUsers(pr);
      }
      return ResponseEntity.ok(p);
    }

    if ("1".equals(lvl)) { // 지사 관리자
      Integer bs = me.getBranchSeq();
      if (bs == null)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("내 지사 정보가 없습니다.");

      Page<Info> p = (keyword != null && !keyword.isBlank())
          ? svc.searchUsersInBranch(keyword.trim(), bs, pr)
          : svc.listUsersInBranch(bs, pr);
      return ResponseEntity.ok(p);
    }

    // 일반 유저
    Page<Info> self = new PageImpl<>(List.of(me), PageRequest.of(0, 1), 1);
    return ResponseEntity.ok(self);
  }

  // -----------------------------------------------------------------
  // 회원정보 종합 수정
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "수정", contents = "회원정보 종합 수정")
  @PutMapping("/{memberSeq}")
  public ResponseEntity<Info> updateMember(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody UpdateRequest req) {

    Info me = requireLogin(authHeader);
    requireReAuth();

    Info tgt = svc.getMyInfoByMemberSeq(memberSeq);
    String myLvl = me.getUserLevel();
    String tgLvl = tgt.getUserLevel();
    Integer myBs = me.getBranchSeq();
    Integer tgBs = tgt.getBranchSeq();

    if ("2".equals(myLvl))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");

    if ("1".equals(myLvl)) {
      if (tgBs == null || !myBs.equals(tgBs))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "같은 지점의 사용자만 수정 가능합니다.");

      boolean self = me.getMemberSeq().equals(memberSeq);
      boolean gen  = "2".equals(tgLvl);
      if (!(self || gen))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 본인 또는 일반유저만 수정 가능합니다.");

      if (req.getUserLevel() != null && !req.getUserLevel().equals(tgLvl))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 userLevel을 변경할 수 없습니다.");

      if (req.getRoleSeq() != null)
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "지사 관리자는 roleSeq를 변경할 수 없습니다.");
    }

    Info updated = svc.updateMemberInfo(memberSeq, req, me.getMemberSeq(), me.getUserId());
    return ResponseEntity.ok(updated);
  }

  // -----------------------------------------------------------------
  // 회원 상세조회
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "조회", contents = "회원 상세조회")
  @GetMapping("/{memberSeq:\\d+}")
  public ResponseEntity<Info> getMemberDetail(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq) {

    Info me = requireLogin(authHeader);
    if (!"0".equals(me.getUserLevel()) && !"1".equals(me.getUserLevel()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.");

    return ResponseEntity.ok(svc.getInfoByMemberSeq(memberSeq));
  }

  // -----------------------------------------------------------------
  // 상태 변경
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "수정", contents = "상태 변경")
  @PutMapping("/{memberSeq}/status")
  public ResponseEntity<String> changeStatus(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable("memberSeq") Integer memberSeq,
      @RequestBody StatusChangeRequest req) {

    Info me = requireLogin(authHeader);
    requireReAuth();
    if (!"0".equals(me.getUserLevel()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본사 관리자만 접근 가능합니다.");

    svc.changeStatus(memberSeq, req);
    return ResponseEntity.ok("상태 변경 완료");
  }

  // -----------------------------------------------------------------
  // 관리자 재인증(비밀번호 확인)
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "재인증", contents = "관리자 재인증")
  @PostMapping("/confirm-password")
  public ResponseEntity<ReauthResponse> confirmPassword(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody ReauthRequest req) {

    Info me = getCurrentUserFromToken(authHeader);
    TmemberEntity user = svc.findEntityByUserId(me.getUserId());
    if (!passwordEncoder.matches(req.getPassword(), user.getUserPass()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비밀번호가 틀렸습니다.");

    String token = jwtTokenProvider.createReAuthToken(me.getUserId());
    long expiresIn = Duration.ofMinutes(30).getSeconds();
    return ResponseEntity.ok(new ReauthResponse(token, expiresIn));
  }

  // -----------------------------------------------------------------
  // 마스킹 여부 수정
  // -----------------------------------------------------------------
  @LogActivity(type = "member", activity = "수정", contents = "마스킹 여부 변경")
  @PutMapping("/mask")
  public ResponseEntity<String> updateMaskFlag(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestBody MaskFlagRequest req) {

    String token = extractToken(authHeader);
    String userId = jwtTokenProvider.getUserId(token);
    Info me = svc.getMyInfoByUserId(userId);
    Integer memberSeq = me.getMemberSeq();

    Integer newFlag = req.getMaskFlag();
    if (newFlag == null || (newFlag != 0 && newFlag != 1))
      return ResponseEntity.badRequest().body("maskFlag 값은 0(마스킹) 또는 1(마스킹 해제)이어야 합니다.");

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
