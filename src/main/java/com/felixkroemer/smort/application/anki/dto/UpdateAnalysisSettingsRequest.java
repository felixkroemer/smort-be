package com.felixkroemer.smort.application.anki.dto;

import com.felixkroemer.smort.domain.common.FormattingMode;

public record UpdateAnalysisSettingsRequest(
    FormattingMode formattingMode, String templateId, String formatInstructions) {}