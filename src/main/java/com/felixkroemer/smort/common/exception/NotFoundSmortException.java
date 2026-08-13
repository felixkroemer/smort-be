package com.felixkroemer.smort.common.exception;

import org.springframework.http.HttpStatus;

public abstract class NotFoundSmortException extends SmortException {

  public NotFoundSmortException(String pattern, Object... args) {
    super(HttpStatus.NOT_FOUND, LogSeverity.INFO, pattern, args);
  }

  protected NotFoundSmortException(
      HttpStatus status, LogSeverity severity, String pattern, Object... args) {
    super(status, severity, pattern, args);
  }
}
