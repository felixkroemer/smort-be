package com.felixkroemer.smort.domain.chat;

import java.util.Map;

public record NoteChatContext<T>(T noteId, Map<String, String> fields) implements ChatContext {}
