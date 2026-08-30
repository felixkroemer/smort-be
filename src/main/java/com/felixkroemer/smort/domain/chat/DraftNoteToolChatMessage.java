package com.felixkroemer.smort.domain.chat;

public record DraftNoteToolChatMessage(
    String callId, String front, String back, ChatMessageMeta meta) implements ChatMessage {}