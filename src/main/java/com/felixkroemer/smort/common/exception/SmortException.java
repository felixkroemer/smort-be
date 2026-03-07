package com.felixkroemer.smort.common.exception;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class SmortException extends RuntimeException {

  public SmortException(Exception e) {
    super(e);
  }

  private SmortException(FormattingTuple ft) {
    super(ft.getMessage(), ft.getThrowable());
  }

  public SmortException(String pattern, Object... args) {
    this(MessageFormatter.arrayFormat(pattern, args));
  }
}
