package com.felixkroemer.smort.application.deck.dto;

import java.util.List;
import java.util.UUID;

public record DeleteNotesRequest(List<UUID> noteIds) {}