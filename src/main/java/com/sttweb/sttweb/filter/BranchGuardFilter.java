// src/main/java/com/sttweb/sttweb/filter/BranchGuardFilter.java
package com.sttweb.sttweb.filter;

import com.sttweb.sttweb.entity.TbranchEntity;
import com.sttweb.sttweb.service.TbranchService;
import com.sttweb.sttweb.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class BranchGuardFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwt;
  private final TbranchService   branchSvc;

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

        /* HQ 여부 = 해당 branch.hqYn == 0 */
        boolean isHq = Optional.ofNullable(userBranchSeq)
            .map(branchSvc::findEntityBySeq)
            .filter(Objects::nonNull)
            .map(b -> "0".equals(b.getHqYn()))   // ← 문자열 비교로 수정
            .orElse(false);

        if (!isHq) {
          /* 서버 IP·포트 → 지사 매핑 */
          String host = Optional.ofNullable(req.getHeader("Host")).orElse("");
          String serverIp   = host.contains(":") ? host.split(":")[0] : req.getLocalAddr();
          int    serverPort = req.getServerPort();

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
    }
    chain.doFilter(req, res);
  }
}
