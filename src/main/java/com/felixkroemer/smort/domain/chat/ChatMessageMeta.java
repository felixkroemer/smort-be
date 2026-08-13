package com.felixkroemer.smort.domain.chat;

import java.time.Instant;
import java.util.Optional;

public record ChatMessageMeta(
    String responseId, Optional<String> previousResponseId, Instant time) {}
