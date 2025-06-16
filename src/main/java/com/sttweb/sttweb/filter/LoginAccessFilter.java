package com.sttweb.sttweb.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class LoginAccessFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    // 프리플라이트는 통과
    if (HttpMethod.OPTIONS.matches(req.getMethod())) return true;
    // POST /api/members/login 이 아니면 스킵
    return !("POST".equals(req.getMethod()) && "/api/members/login".equals(req.getServletPath()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain chain
  ) throws ServletException, IOException {
    // request body는 절대 읽지 않는다!
    chain.doFilter(request, response);
  }
}
