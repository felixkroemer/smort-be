package com.felixkroemer.smort.application.user.dto;

import com.felixkroemer.smort.domain.user.TemplateSource;

public record FormattingTemplateResponse(
    String id, String name, String content, TemplateSource type) {}
