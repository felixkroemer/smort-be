package com.felixkroemer.smort.domain.chat;

public record DraftNoteToolChatResponse(
    String callId, String front, String back, ChatMessageMeta meta)
    implements ChatResponse {}