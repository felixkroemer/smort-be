package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record AnalysisResponse(
    UUID id, String status, Optional<String> deckId, Instant updatedAt) {}
