package com.felixkroemer.smort.application.anki.dto;

import java.util.List;

public record SourceNoteResponse(Long id, List<String> flds, String guid) {}
