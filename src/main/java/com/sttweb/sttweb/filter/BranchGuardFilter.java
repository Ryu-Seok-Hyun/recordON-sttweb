// src/main/java/com/sttweb/sttweb/filter/BranchGuardFilter.java
package com.sttweb.sttweb.filter;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@RequiredArgsConstructor
public class BranchGuardFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwt;
  private final TbranchService branchSvc;

  @Override
  protected void doFilterInternal(HttpServletRequest req,
      HttpServletResponse res,
      FilterChain chain)
      throws ServletException, IOException {
    String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith("Bearer ")) {
      String token = auth.substring(7);
      if (jwt.validateToken(token)) {
        Integer userBranchSeq = jwt.getBranchSeq(token);

        // branch.hqYn == "0" 이면 HQ
        boolean isHq = Optional.ofNullable(userBranchSeq)
            .map(branchSvc::findEntityBySeq)
            .filter(Objects::nonNull)
            .map(b -> "0".equals(b.getHqYn()))
            .orElse(false);

        // 수정: HQ 계정이면 IP 검사 스킵
        if (isHq) {
          chain.doFilter(req, res);
          return;
        }

        // 지점 사용자만 server IP/port → branchSeq 비교
        String hostHeader = Optional.ofNullable(req.getHeader("Host")).orElse("");
        String serverIp = hostHeader.contains(":")
            ? hostHeader.split(":")[0]
            : req.getLocalAddr();
        int serverPort = req.getServerPort();

        Integer serverBranchSeq = branchSvc.findBypIp(serverIp)
            .filter(b -> b.getPPort().equals(serverPort))
            .map(TbranchEntity::getBranchSeq)
            .orElse(null);

        if (serverBranchSeq != null && !serverBranchSeq.equals(userBranchSeq)) {
          res.sendError(HttpServletResponse.SC_FORBIDDEN,
              "다른 지사 서버로 접근했습니다.");
          return;
        }
      }
    }
    chain.doFilter(req, res);
  }
}
