package com.felixkroemer.smort.common.exception;

public class DeckNotFoundSmortException extends NotFoundSmortException {

  public DeckNotFoundSmortException(Long id) {
    super("Deck not found: " + id);
  }
}
