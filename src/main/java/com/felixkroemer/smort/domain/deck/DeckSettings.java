package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record DeckSettings(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}