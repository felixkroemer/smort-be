package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;

public record ChatMessageResponse(String role, String content, Instant createdAt) {}
