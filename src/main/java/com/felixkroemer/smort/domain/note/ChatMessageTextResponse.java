package com.felixkroemer.smort.domain.note;

public record ChatMessageTextResponse(String text, ChatMessageResponseMeta meta)
    implements ChatMessageResponse {}
