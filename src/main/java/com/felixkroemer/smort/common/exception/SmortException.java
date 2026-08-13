package com.felixkroemer.smort.common.exception;

import lombok.Getter;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.http.HttpStatus;

@Getter
public class SmortException extends RuntimeException {

  private final HttpStatus httpStatus;
  private LogSeverity severity;

  private SmortException(HttpStatus status, LogSeverity severity, String msg, Throwable cause) {
    super(msg, cause);
    this.httpStatus = status;
    this.severity = severity;
  }

  private SmortException(FormattingTuple ft) {
    this(HttpStatus.INTERNAL_SERVER_ERROR, LogSeverity.ERROR, ft.getMessage(), ft.getThrowable());
  }

  public SmortException(HttpStatus status, LogSeverity severity, String msg) {
    super(msg);
    this.httpStatus = status;
    this.severity = severity;
  }

  public SmortException(String msg) {
    this(msg, HttpStatus.INTERNAL_SERVER_ERROR, LogSeverity.ERROR);
  }

  public SmortException(String pattern, Object... args) {
    this(MessageFormatter.arrayFormat(pattern, args));
  }

  public SmortException(HttpStatus status, LogSeverity severity, String pattern, Object... args) {
    var ft = MessageFormatter.arrayFormat(pattern, args);
    this(status, severity, ft.getMessage(), ft.getThrowable());
  }

  public SmortException withSeverity(LogSeverity severity) {
    this.severity = severity;
    return this;
  }
}
