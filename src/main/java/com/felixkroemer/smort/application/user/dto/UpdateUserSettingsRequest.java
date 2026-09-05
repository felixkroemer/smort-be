package com.felixkroemer.smort.application.user.dto;

import java.util.Optional;

public record UpdateUserSettingsRequest(Optional<String> defaultTemplateId) {}
