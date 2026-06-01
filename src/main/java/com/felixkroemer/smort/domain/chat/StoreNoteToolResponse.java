package com.felixkroemer.smort.domain.chat;

public record StoreNoteToolResponse(
    String toolName, String callId, String front, String back, ChatMessageResponseMeta meta)
    implements ChatMessageResponse {}
