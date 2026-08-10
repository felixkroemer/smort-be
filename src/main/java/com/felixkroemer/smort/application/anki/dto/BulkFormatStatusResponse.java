package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;

public record BulkFormatStatusResponse(
    String status, Instant createdAt, Instant lastUpdatedAt, int totalNotes, int completedNotes,
    int failedCount, int attempts) {}
