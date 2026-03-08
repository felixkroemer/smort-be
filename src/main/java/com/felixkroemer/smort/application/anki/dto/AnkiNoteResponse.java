package com.felixkroemer.smort.application.anki.dto;

import java.util.List;

public record AnkiNoteResponse(Long id, List<String> flds) {}
