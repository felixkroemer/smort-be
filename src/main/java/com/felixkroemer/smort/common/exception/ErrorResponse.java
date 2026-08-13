package com.felixkroemer.smort.common.exception;

import org.springframework.http.HttpStatus;

public record ErrorResponse(HttpStatus status, String message) {

  public ErrorResponse(SmortException ex) {
    this(ex.getHttpStatus(), ex.getMessage());
  }
}
