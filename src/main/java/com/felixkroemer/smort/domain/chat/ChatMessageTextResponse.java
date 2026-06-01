package com.felixkroemer.smort.domain.chat;

public record ChatMessageTextResponse(String text, ChatMessageResponseMeta meta)
    implements ChatMessageResponse {}
