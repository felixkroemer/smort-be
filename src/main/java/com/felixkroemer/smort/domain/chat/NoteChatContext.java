package com.felixkroemer.smort.domain.chat;

import java.util.Map;
import java.util.UUID;

public record NoteChatContext(UUID noteId, Map<String, String> fields) implements ChatContext {}
