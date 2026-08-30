package com.felixkroemer.smort.application.chat.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record ChatMessageResponse(
    String type,
    Optional<String> response,
    Optional<String> toolName,
    Optional<String> message,
    boolean userInitiated,
    Instant createdAt,
    Map<String, String> arguments) {}
