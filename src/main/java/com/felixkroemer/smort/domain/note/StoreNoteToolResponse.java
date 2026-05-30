package com.felixkroemer.smort.domain.note;


public record StoreNoteToolResponse(
    String toolName, String callId, String front, String back, ChatMessageResponseMeta meta)
    implements ChatMessageResponse {}
