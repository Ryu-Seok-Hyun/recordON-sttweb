package com.sttweb.sttweb.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.entity.TmemberEntity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.service.TmemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

@RequiredArgsConstructor
public class LoginAccessFilter extends OncePerRequestFilter {

  private final TbranchService branchSvc;
  private final TmemberService memberSvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static Set<String> getAllLocalIps() {
    Set<String> ips = new HashSet<>();
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      while (nics.hasMoreElements()) {
        NetworkInterface nic = nics.nextElement();
        Enumeration<InetAddress> addrs = nic.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress addr = addrs.nextElement();
          if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
            ips.add(addr.getHostAddress());
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return ips;
  }


  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain
  ) throws ServletException, IOException {

    // 1) 멀티리드 요청 래핑
    MultiReadHttpServletRequest wrap = new MultiReadHttpServletRequest(request);

    // 2) 서버 IP/포트 로그
    Set<String> localIps = getAllLocalIps();
    int serverPort    = wrap.getServerPort();
    System.out.println("[DEBUG] 서버 NIC IP 목록: " + localIps + ", 서버 포트: " + serverPort);

    // 3) JSON 본문에서 userId 추출
    String body = new String(wrap.getInputStream().readAllBytes(), wrap.getCharacterEncoding());
    String userId = null;
    if (wrap.getContentType() != null && wrap.getContentType().contains("application/json")) {
      System.out.println("[DEBUG] 요청 BODY 내용: " + body);
      JsonNode json = objectMapper.readTree(body);
      userId = json.has("userId") ? json.get("userId").asText() : null;
      System.out.println("[DEBUG] 추출된 userId: " + userId);
    }

    // 4) 폼 파라미터에서 userId
    if (userId == null || userId.isBlank()) {
      userId = wrap.getParameter("userId");
      System.out.println("[DEBUG] 파라미터로부터 userId 추출됨: " + userId);
    }

    // 5) userId가 없으면 로그인 로직으로 패스
    if (userId == null || userId.isBlank()) {
      chain.doFilter(wrap, response);
      return;
    }

    // 6) DB 조회 & 본사(HQ) 여부
    TmemberEntity user = memberSvc.findEntityByUserId(userId);
    if (user == null) {
      chain.doFilter(wrap, response);
      return;
    }
    TbranchEntity home = branchSvc.findEntityBySeq(user.getBranchSeq());
    boolean isHqUser = home != null && "0".equals(home.getHqYn());
    if (isHqUser) {
      System.out.println("[DEBUG] 본사 사용자로 확인됨. 필터 통과");
      chain.doFilter(wrap, response);
      return;
    }

    // 7) 지사 매칭 검사
    boolean matched = false;
    Integer matchedBranchSeq = null;
    for (TbranchEntity b : branchSvc.findAllEntities()) {
      String pip  = Optional.ofNullable(b.getPIp()).orElse("").trim();
      String pbip = Optional.ofNullable(b.getPbIp()).orElse("").trim();
      int pPort   = Optional.ofNullable(b.getPPort()).map(Integer::parseInt).orElse(-1);
      int pbPort  = Optional.ofNullable(b.getPbPort()).map(Integer::parseInt).orElse(-1);

      if ((localIps.contains(pip) && pPort == serverPort) ||
          (localIps.contains(pbip) && pbPort == serverPort)) {
        matched = true;
        matchedBranchSeq = b.getBranchSeq();
        System.out.printf("[DEBUG] 매칭 성공 - 지점[%d] (IP=%s,%s  PORT=%d)%n",
            b.getBranchSeq(), pip, pbip, serverPort);
        break;
      }
    }
    System.out.printf("[DEBUG] 최종 매칭 결과 - matched=%b, 내지사=%d, 서버지사=%s%n",
        matched, home.getBranchSeq(), matchedBranchSeq);

    if (!matched || !Objects.equals(matchedBranchSeq, home.getBranchSeq())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(
          "{\"message\":\"해당 지사 서버에서는 본사 또는 해당 지사 사용자만 로그인할 수 있습니다.\"}"
      );
      System.out.println("[DEBUG] 로그인 차단됨");
      return;
    }

    System.out.println("[DEBUG] 필터 통과: 서버와 사용자 지사 일치");
    // 8) **반드시** 래퍼를 넘겨야 본문이 살아있습니다!
    chain.doFilter(wrap, response);
  }
}