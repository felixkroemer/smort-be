package com.felixkroemer.smort.domain.note;

import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;

public record DerivedNoteExportEntry(String guid, DerivedNoteEntity derivedNote) {}