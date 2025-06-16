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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider              jwtTokenProvider;
  private final JwtAuthenticationEntryPoint   entryPoint;

  /** 필터를 타지 않을 URL 패턴 */
  private static final List<String> WHITELIST = List.of(
      "/api/members/signup",
      "/api/members/login",
      "/api/members/logout",
      "/api/members/confirm-password"
  );
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
      JwtAuthenticationEntryPoint entryPoint) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.entryPoint       = entryPoint;
  }

  /** 로그인·회원가입·OPTIONS 등은 필터를 건너뜀 */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain        chain)
      throws ServletException, IOException {

    try {
      /* ───── 1) 표준 Bearer 토큰 ───── */
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        if (jwtTokenProvider.validateToken(token)) {

          String  userId     = jwtTokenProvider.getUserId(token);
          String  userLevel  = jwtTokenProvider.getUserLevel(token);      // HQ 판별용
          List<String> roles = jwtTokenProvider.getRoles(token);          // List<String>

          /* 권한 매핑 */
          List<SimpleGrantedAuthority> authorities = new ArrayList<>();

          // 본사 레벨("0") → ROLE_ADMIN
          if ("0".equals(userLevel)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
          }

          // 토큰 roles → ROLE_xxx 형태로 매핑
          if (roles != null) {
            authorities.addAll(roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));
          }

          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(userId, null, authorities);

          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      }

      /* ───── 2) 재인증 토큰 (X-ReAuth-Token 헤더) ───── */
      String reAuthHeader = request.getHeader("X-ReAuth-Token");
      if (reAuthHeader != null && jwtTokenProvider.validateReAuthToken(reAuthHeader)) {
        String userId = jwtTokenProvider.getUserId(reAuthHeader);
        var    auth   = new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_REAUTH")));
        SecurityContextHolder.getContext().setAuthentication(auth);
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
