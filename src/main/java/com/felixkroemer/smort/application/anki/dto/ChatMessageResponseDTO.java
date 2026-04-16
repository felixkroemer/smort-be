package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;
import java.util.Optional;

// TODO: must also contain the content of the derived note (which may have been updated by the chat)
public record ChatMessageResponseDTO(
    String type,
    Optional<String> response,
    Optional<String> toolName,
    Optional<String> message,
    Instant createdAt) {}
