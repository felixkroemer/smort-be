package com.felixkroemer.smort.application.deck.dto;

import java.util.List;
import java.util.UUID;

public record MoveNotesRequest(List<UUID> noteIds, UUID targetDeckId) {}
