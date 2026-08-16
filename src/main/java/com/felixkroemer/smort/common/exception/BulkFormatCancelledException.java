package com.felixkroemer.smort.common.exception;

public class BulkFormatCancelledException extends SmortException {

  public BulkFormatCancelledException(String pattern, Object... args) {
    super(pattern, args);
  }
}
