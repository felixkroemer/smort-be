package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import java.time.Instant;
import java.util.UUID;

public final class AnkiKeys {

  private AnkiKeys() {}

  public static String pk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }

  public static String derivedNoteSk(Long deckId, Long noteId) {
    return "NOTE#" + deckId + "#" + noteId;
  }

  public static String derivedNotePrefix(Long deckId) {
    return "NOTE#" + deckId;
  }

  public static String allDerivedNotesPrefix() {
    return "NOTE";
  }

  public static String chatMessageSk(
      Long deckId, Long noteId, Instant createdAt, String responseId) {
    return "CHAT#" + deckId + "#" + noteId + "#" + createdAt + "#" + responseId;
  }

  public static String chatMessagePrefix(Long deckId, Long noteId) {
    return "CHAT#" + deckId + "#" + noteId;
  }
}
