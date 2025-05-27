package com.sttweb.sttweb.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint entryPoint;

  // 토큰 검증을 건너뛸 URI 목록
  private static final List<String> WHITELIST = List.of(
      "/api/members/signup",
      "/api/members/login",
      "/api/members/logout",
      "/api/members/confirm-password"  // 재인증 엔드포인트도 허용
  );

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
      JwtAuthenticationEntryPoint entryPoint) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.entryPoint = entryPoint;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    if (WHITELIST.contains(path)) {
      chain.doFilter(request, response);
      return;
    }

    try {
      // 1) 일반 로그인 토큰
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7).trim();
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

      // 2) 재인증 토큰
      String reAuthHeader = request.getHeader("X-ReAuth-Token");
      if (reAuthHeader != null && jwtTokenProvider.validateReAuthToken(reAuthHeader)) {
        String userId = jwtTokenProvider.getUserId(reAuthHeader);
        List<SimpleGrantedAuthority> reAuthAuthorities = List.of(
            new SimpleGrantedAuthority("ROLE_REAUTH")
        );
        var reAuth = new UsernamePasswordAuthenticationToken(userId, null, reAuthAuthorities);
        SecurityContextHolder.getContext().setAuthentication(reAuth);
      }

      chain.doFilter(request, response);

    } catch (ExpiredJwtException ex) {
      SecurityContextHolder.clearContext();
      entryPoint.commence(request, response,
          new InsufficientAuthenticationException("토큰 만료", ex));

    } catch (JwtException | IllegalArgumentException ex) {
      SecurityContextHolder.clearContext();
      entryPoint.commence(request, response,
          new InsufficientAuthenticationException("토큰 오류", ex));
    }
  }
}
