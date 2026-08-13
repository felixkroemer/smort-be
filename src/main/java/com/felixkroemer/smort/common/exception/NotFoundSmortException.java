package com.felixkroemer.smort.common.exception;

import org.springframework.http.HttpStatus;

public abstract class NotFoundSmortException extends SmortException {

  protected NotFoundSmortException(String msg) {
    super(msg, HttpStatus.NOT_FOUND, LogSeverity.INFO);
  }

  protected NotFoundSmortException(String msg, HttpStatus status, LogSeverity severity) {
    super(msg, status, severity);
  }
}
