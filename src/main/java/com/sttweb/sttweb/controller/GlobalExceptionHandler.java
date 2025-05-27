package com.sttweb.sttweb.controller;

import com.sttweb.sttweb.exception.ForbiddenException;
import com.sttweb.sttweb.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;

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
}
