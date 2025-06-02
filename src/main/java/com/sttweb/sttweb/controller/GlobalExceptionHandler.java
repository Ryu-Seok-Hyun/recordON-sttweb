package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.exception.UnauthorizedException;
import java.util.HashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadCredentials(IllegalArgumentException ex) {
    // 로그인 실패(아이디/비번 불일치)
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Collections.singletonMap("msg", ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleDisabledUser(IllegalStateException ex) {
    // 비활성 계정 차단
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Collections.singletonMap("msg", ex.getMessage()));
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<Map<String, String>> handle401(UnauthorizedException ex) {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Collections.singletonMap("msg", ex.getReason()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, String>> handle403(ForbiddenException ex) {
    String reason = ex.getReason();
    String msg = reason.contains("재인증")
        ? "REAUTH_REQUIRED"
        : "ACCESS_DENIED";

    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Collections.singletonMap("msg", msg));
  }

  /**
   * ResponseStatusException 이 발생했을 때 JSON 형태로 응답 본문을 내려준다.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
    // 응답 바디에 message 필드만 담아서 보낸다.
    Map<String, String> body = new HashMap<>();
    body.put("message", ex.getReason());

    // 예: ex.getStatusCode() 가 403 Forbidden 이면, 403 상태와 { "message": "…"} 를 클라이언트에게 반환
    return ResponseEntity
        .status(ex.getStatusCode())
        .body(body);
  }

  /**
   * 만약 403 / AccessDeniedException 같은 다른 예외를 JSON으로 처리하고 싶으면
   * 별도 핸들러를 추가해도 된다. (예시: Spring Security에서 AccessDeniedException 발생 시)
   */
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<Map<String, String>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
    Map<String, String> body = new HashMap<>();
    body.put("message", "권한이 없습니다.");
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(body);
  }
}
