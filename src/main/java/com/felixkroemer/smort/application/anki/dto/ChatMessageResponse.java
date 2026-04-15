package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;

// TODO: must also contain the content of the derived note (which may have been updated by the chat)
public record ChatMessageResponse(String message, String response, Instant createdAt) {}
