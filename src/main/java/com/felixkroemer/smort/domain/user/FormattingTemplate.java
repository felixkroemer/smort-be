package com.felixkroemer.smort.domain.user;

public record FormattingTemplate(
    String id, String name, String content, TemplateSource source) {}
