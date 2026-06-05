package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.time.Instant;

public final class ChatKeys {

  public static String chatMessageSk(String noteId, Instant createdAt, String responseId) {
    return "CHAT#" + noteId + "#" + createdAt + "#" + responseId;
  }

  public static <T> String chatMessagePrefix(T noteId) {
    return "CHAT#" + noteId + "#";
  }
}
