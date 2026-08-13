package com.felixkroemer.smort.common.exception;

import lombok.Getter;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.http.HttpStatus;

@Getter
public class SmortException extends RuntimeException {

  private final HttpStatus httpStatus;
  private LogSeverity severity;

  public SmortException(String msg, HttpStatus status, LogSeverity severity) {
    super(msg);
    this.httpStatus = status;
    this.severity = severity;
  }

  public SmortException(String msg) {
    this(msg, HttpStatus.INTERNAL_SERVER_ERROR, LogSeverity.ERROR);
  }

  public SmortException(Exception e) {
    this(e.getMessage(), e, HttpStatus.INTERNAL_SERVER_ERROR, LogSeverity.ERROR);
  }

  private SmortException(FormattingTuple ft) {
    this(ft.getMessage(), ft.getThrowable(), HttpStatus.INTERNAL_SERVER_ERROR, LogSeverity.ERROR);
  }

  public SmortException(String pattern, Object... args) {
    this(MessageFormatter.arrayFormat(pattern, args));
  }

  private SmortException(String msg, Throwable cause, HttpStatus status, LogSeverity severity) {
    super(msg, cause);
    this.httpStatus = status;
    this.severity = severity;
  }

  public SmortException withSeverity(LogSeverity severity) {
    this.severity = severity;
    return this;
  }
}
