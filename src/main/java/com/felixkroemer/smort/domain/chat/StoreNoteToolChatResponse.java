package com.felixkroemer.smort.domain.chat;

public record StoreNoteToolChatResponse(
    String callId, String front, String back, ChatMessageMeta meta)
    implements ChatResponse {}