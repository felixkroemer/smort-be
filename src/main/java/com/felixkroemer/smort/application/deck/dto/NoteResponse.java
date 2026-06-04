package com.felixkroemer.smort.application.deck.dto;

import java.util.UUID;

public record NoteResponse(UUID id, String front, String back) {}
