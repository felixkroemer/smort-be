package com.felixkroemer.smort.application.anki.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DerivedNoteResponse(
        String sfld,
        Map<String, String> fields,
        List<String> tags,
        Instant createdAt
) {}