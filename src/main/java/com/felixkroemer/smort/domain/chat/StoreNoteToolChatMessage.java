package com.felixkroemer.smort.domain.chat;

public record StoreNoteToolChatMessage(
    String callId, String front, String back, ChatMessageMeta meta) implements ChatMessage {}
