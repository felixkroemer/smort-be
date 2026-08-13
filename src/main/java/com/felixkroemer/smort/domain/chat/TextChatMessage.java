package com.felixkroemer.smort.domain.chat;

public record TextChatMessage(String text, ChatMessageMeta meta) implements ChatMessage {}
