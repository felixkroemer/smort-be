package com.felixkroemer.smort.application.anki.dto;

import java.util.List;

public record NoteResponse(Long id, List<String> flds) {}
