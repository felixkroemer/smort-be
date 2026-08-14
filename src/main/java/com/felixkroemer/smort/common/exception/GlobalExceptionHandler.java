package com.felixkroemer.smort.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(SmortException.class)
  public ResponseEntity<ErrorResponse> handle(SmortException ex) {
    switch (ex.getSeverity()) {
      case ERROR -> log.error(ex.getMessage(), ex);
      case WARN -> log.warn(ex.getMessage(), ex);
      case INFO -> log.info(ex.getMessage());
    }
    return ResponseEntity.status(ex.getHttpStatus()).body(new ErrorResponse(ex));
  }
}
