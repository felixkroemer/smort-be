package com.felixkroemer.smort.application.deck.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record DeckSettingsResponse(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}