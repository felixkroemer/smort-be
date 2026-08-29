package com.felixkroemer.smort.infrastructure.dynamodb.keys.sort;

import java.time.Instant;

public final class ChatKeys {

  public static String chatMessageSk(String entityId, Instant createdAt, String responseId, boolean userInitiated) {
    return "CHAT#" + (userInitiated ? "U#" : "C#") + entityId + "#" + createdAt + "#" + responseId;
  }

  public static <T> String llmChatMessagesPrefix(T entityId) {
    return "CHAT#C#" + entityId + "#";
  }

  public static <T> String userChatMessagesPrefix(T entityId) {
    return "CHAT#U#" + entityId + "#";
  }
}
