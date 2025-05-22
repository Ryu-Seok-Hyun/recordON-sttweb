// src/main/java/com/sttweb/sttweb/jwt/JwtAuthenticationFilter.java
package com.sttweb.sttweb.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenProvider jwtTokenProvider;

  // 토큰 검증을 건너뛸 URI 목록
  private static final List<String> WHITELIST = List.of(
      "/api/members/signup",
      "/api/members/login",
      "/api/members/logout"
  );

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {

    String path = request.getRequestURI();

    // 1) 화이트리스트 URI는 인증 없이 바로 통과
    if (WHITELIST.contains(path)) {
      chain.doFilter(request, response);
      return;
    }

    try {
      String header = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (header != null && header.startsWith("Bearer ")) {
        String token = header.substring(7).trim();

        if (jwtTokenProvider.validateToken(token)) {
          String userId = jwtTokenProvider.getUserId(token);
          String roleCode = jwtTokenProvider.getRoles(token);

          List<SimpleGrantedAuthority> authorities = new ArrayList<>();
          if ("0".equals(roleCode)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
          } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
          }

          var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    } catch (Exception e) {
      logger.error("[JwtFilter] JWT 처리 중 예외 발생", e);
      SecurityContextHolder.clearContext();
    }

    // 2) 다음 필터 진행
    chain.doFilter(request, response);
  }
}
