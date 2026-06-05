package com.felixkroemer.smort.application.deck.dto;

import java.util.Map;
import java.util.UUID;

public record ImportAnalysisRequest(UUID id, Map<String, NoteTypeTemplate> templates) {}
