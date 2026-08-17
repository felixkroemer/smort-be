package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.time.Instant;

public final class ChatKeys {

  public static String chatMessageSk(String noteId, Instant createdAt, String responseId, boolean userInitiated) {
    return "CHAT#" + (userInitiated ? "U#" : "C#") + noteId + "#" + createdAt + "#" + responseId;
  }

  public static <T> String allChatMessagesPrefix(T noteId) {
    return "CHAT#" + noteId + "#";
  }

  public static <T> String llmChatMessagesPrefix(T noteId) {
    return "CHAT#" + noteId + "#C#";
  }
}
