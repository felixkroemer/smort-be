package com.felixkroemer.smort.domain.chat;

public record TextChatMessage(String response, ChatMessageMeta meta) implements ChatMessage {}
