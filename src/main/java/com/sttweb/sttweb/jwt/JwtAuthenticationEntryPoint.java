package com.sttweb.sttweb.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
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

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Throwable cause = authException.getCause();
    String code;
    String message;

    if (cause instanceof ExpiredJwtException) {
      code = "EXPIRED_TOKEN";
      message = "토큰이 만료되었습니다.";
    } else {
      code = "UNAUTHORIZED";
      message = "인증되지 않은 요청입니다.";
    }

    Map<String, String> body = new HashMap<>();
    body.put("code", code);
    body.put("message", message);

    objectMapper.writeValue(response.getWriter(), body);
  }
}
