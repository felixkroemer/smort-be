package com.felixkroemer.smort.application.user.dto;

import java.util.List;

public record UserSettingsResponse(
    String defaultTemplateId, List<FormattingTemplateResponse> templates) {}
