package com.sttweb.sttweb.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadCredentials(IllegalArgumentException ex) {
    // 로그인 실패(아이디/비번 불일치)
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleDisabledUser(IllegalStateException ex) {
    // 비활성 계정 차단
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
  }
}