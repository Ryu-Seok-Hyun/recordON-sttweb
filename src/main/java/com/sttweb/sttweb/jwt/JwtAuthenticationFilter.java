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
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtAuthenticationEntryPoint entryPoint;

  // 필터를 타지 않을 URL 패턴
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
    this.entryPoint = entryPoint;
  }

  /**
   * 로그인·회원가입·OPTIONS 등은 필터를 아예 타지 않도록 건너뛴다.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // 1) 프리플라이트
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    // 2) 화이트리스트 URL
    return WHITELIST.stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain)
      throws ServletException, IOException {
    try {
      // 1) 표준 Bearer 토큰 처리
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        if (jwtTokenProvider.validateToken(token)) {
          String userId   = jwtTokenProvider.getUserId(token);
          String roleCode = jwtTokenProvider.getRoles(token);

          var authorities = new ArrayList<SimpleGrantedAuthority>();
          authorities.add("0".equals(roleCode)
              ? new SimpleGrantedAuthority("ROLE_ADMIN")
              : new SimpleGrantedAuthority("ROLE_USER"));

          var auth = new UsernamePasswordAuthenticationToken(
              userId, null, authorities
          );
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }

      // 2) 재인증 토큰 처리 (X-ReAuth-Token)
      String reAuth = request.getHeader("X-ReAuth-Token");
      if (reAuth != null && jwtTokenProvider.validateReAuthToken(reAuth)) {
        String userId = jwtTokenProvider.getUserId(reAuth);
        var auth = new UsernamePasswordAuthenticationToken(
            userId, null,
            List.of(new SimpleGrantedAuthority("ROLE_REAUTH"))
        );
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
