// src/main/java/com/sttweb/sttweb/exception/ResourceNotFoundException.java
package com.sttweb.sttweb.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 없는 리소스 조회 시 404 반환
 */
public class ResourceNotFoundException extends ResponseStatusException {
  public ResourceNotFoundException(String reason) {
    super(HttpStatus.NOT_FOUND, reason);
  }
}
