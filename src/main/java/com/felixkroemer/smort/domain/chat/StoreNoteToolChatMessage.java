package com.felixkroemer.smort.domain.chat;

public record StoreNoteToolChatMessage(
    String toolName, String callId, String front, String back, ChatMessageMeta meta)
    implements ChatMessage {}
