package com.felixkroemer.smort.common.exception;

import org.springframework.http.HttpStatus;

public class DeckArchivedSmortException extends NotFoundSmortException {

  public DeckArchivedSmortException(Long id) {
    super("Deck archived: " + id, HttpStatus.GONE, LogSeverity.WARN);
  }
}
