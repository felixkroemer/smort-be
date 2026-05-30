package com.felixkroemer.smort.application.anki.dto;

import java.util.Map;

public record NoteResponse(Long id, Map<String, String> flds, String guid) {}
