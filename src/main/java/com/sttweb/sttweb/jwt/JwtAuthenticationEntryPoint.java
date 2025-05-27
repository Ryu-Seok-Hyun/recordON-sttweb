package com.sttweb.sttweb.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
// 토큰 만료시 응답 연관 코드
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void commence(HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    // 기본 401
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Throwable cause = authException.getCause();
    String msg;
    if (cause instanceof ExpiredJwtException) {
      msg = "TOKEN_EXPIRED";
    } else {
      msg = "UNAUTHORIZED";
    }

    // {"msg":"TOKEN_EXPIRED"} 또는 {"msg":"UNAUTHORIZED"} 반환
    objectMapper.writeValue(response.getWriter(),
        Collections.singletonMap("msg", msg));
  }
}
