package com.felixkroemer.smort.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends SmortException {

  public NotFoundException(String pattern, Object... args) {
    super(HttpStatus.NOT_FOUND, LogSeverity.INFO, pattern, args);
  }

}
