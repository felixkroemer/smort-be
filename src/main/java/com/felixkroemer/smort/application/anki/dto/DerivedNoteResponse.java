package com.felixkroemer.smort.application.anki.dto;

import java.util.List;

public record DerivedNoteResponse(Long id, List<String> flds) {}
