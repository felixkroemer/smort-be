package com.felixkroemer.smort.domain.note;

import java.time.Instant;
import java.util.Optional;

public record ChatMessageResponseMeta(String responseId, Optional<String> previousResponseId, Instant time) {}
