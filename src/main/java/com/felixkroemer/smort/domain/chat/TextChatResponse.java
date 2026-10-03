package com.felixkroemer.smort.domain.chat;

public record TextChatResponse(String response, ChatMessageMeta meta)
    implements ChatResponse {}