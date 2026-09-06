package com.felixkroemer.smort.application.deck.dto;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(UUID id, UUID deckId, String front, String back, Instant lastFormattedAt) {}
