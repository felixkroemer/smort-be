package com.felixkroemer.smort.infrastructure.dynamodb.anki;

import java.time.Instant;
import java.util.UUID;

public final class AnkiKeys {

  private AnkiKeys() {}

  public static String pk(UUID analysisId) {
    return "ANALYSIS#" + analysisId;
  }

  public static String derivedNoteSk(Long noteId) {
    return "NOTE#" + noteId;
  }

  public static String derivedNotePrefix(Long deckId) {
    return "NOTE";
  }

  public static String chatMessageSk(Long noteId, Instant createdAt, String responseId) {
    return "CHAT#" + noteId + "#" + createdAt + "#" + responseId;
  }

  public static String chatMessagePrefix(Long noteId) {
    return "CHAT#" + noteId;
  }
}
