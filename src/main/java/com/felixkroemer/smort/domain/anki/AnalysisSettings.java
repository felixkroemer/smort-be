package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record AnalysisSettings(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}