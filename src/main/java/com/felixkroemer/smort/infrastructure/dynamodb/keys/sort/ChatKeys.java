package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.time.Instant;

public final class ChatKeys {

  public static String chatMessageSk(Long noteId, Instant createdAt, String responseId) {
    return "CHAT#" + noteId + "#" + createdAt + "#" + responseId;
  }

  public static String chatMessagePrefix(Long noteId) {
    return "CHAT#" + noteId + "#";
  }
}
