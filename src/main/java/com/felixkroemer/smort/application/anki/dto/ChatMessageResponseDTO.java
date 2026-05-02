package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;
import java.util.Optional;

public record ChatMessageResponseDTO(
    String type,
    Optional<String> response,
    Optional<String> toolName,
    Optional<String> message,
    Instant createdAt) {}
