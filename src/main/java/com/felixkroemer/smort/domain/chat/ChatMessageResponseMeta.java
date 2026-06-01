package com.felixkroemer.smort.domain.chat;

import java.time.Instant;
import java.util.Optional;

public record ChatMessageResponseMeta(
    String responseId, Optional<String> previousResponseId, Instant time) {}
