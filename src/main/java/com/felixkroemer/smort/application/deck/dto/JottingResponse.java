package com.felixkroemer.smort.application.deck.dto;

import java.util.UUID;

public record JottingResponse(UUID id, UUID deckId, String title, String description) {}
