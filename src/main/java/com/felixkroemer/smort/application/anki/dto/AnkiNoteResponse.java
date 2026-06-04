package com.felixkroemer.smort.application.anki.dto;

import java.util.Map;

public record AnkiNoteResponse(Long id, Map<String, String> flds, String guid) {}
