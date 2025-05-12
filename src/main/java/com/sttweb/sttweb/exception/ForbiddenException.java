// src/main/java/com/sttweb/sttweb/exception/ForbiddenException.java
package com.sttweb.sttweb.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ForbiddenException extends ResponseStatusException {
  public ForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, message);
  }
}
