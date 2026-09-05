package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;

public record DerivedNoteResponse(Long id, String front, String back, Instant lastFormattedAt) {}
