package com.felixkroemer.smort.application.deck.dto;

import java.util.Optional;

public record UpdateDeckSettingsRequest(Optional<String> formatInstructions) {}