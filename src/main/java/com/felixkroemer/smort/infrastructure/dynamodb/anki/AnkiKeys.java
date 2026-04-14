package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import java.time.Instant;
import java.util.UUID;

public final class AnkiKeys {

  private AnkiKeys() {}

  public static String pk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }

  public static String deckPrefix(Long deckId) {
    return "DECK#" + deckId;
  }

  public static String derivedNoteSk(Long deckId, Long sourceNoteId) {
    return "DECK#" + deckId + "#NOTE#" + sourceNoteId;
  }

  public static String chatMessageSk(
      Long deckId, Long sourceNoteId, Instant createdAt, String responseId) {
    return "DECK#" + deckId + "#NOTE#" + sourceNoteId + "#CHAT#" + createdAt + "#" + responseId;
  }

  public static String chatMessagePrefix(Long deckId, Long sourceNoteId) {
    return "DECK#" + deckId + "#NOTE#" + sourceNoteId + "#CHAT#";
  }
}
