package com.felixkroemer.smort.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(SmortException.class)
  public ResponseEntity<ErrorResponse> handle(SmortException ex) {
    switch (ex.getSeverity()) {
      case ERROR -> log.error("Unhandled: {}", ex.getMessage(), ex);
      case WARN -> log.warn("Warning: {}", ex.getMessage(), ex);
      case INFO -> log.info("Handled: {}", ex.getMessage());
    }
    return ResponseEntity.status(ex.getHttpStatus()).body(new ErrorResponse(ex));
  }
}
