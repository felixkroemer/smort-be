package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnkiNoteTypeService {

  private final AnkiNoteRepository ankiNoteRepository;

  public Map<Long, AnkiNoteTypeEntity> getNoteTypesByAnalysisId(UUID analysisId) {
    var noteTypes = ankiNoteRepository.findNoteTypesByAnalysisId(analysisId);
    return noteTypes.stream()
        .collect(Collectors.toMap(AnkiNoteTypeEntity::getId, Function.identity()));
  }

  public Map<String, String> getContent(UUID analysisId, AnkiNoteEntity note) {
    var noteType = getNoteTypesByAnalysisId(analysisId).get(note.getNoteTypeId());
    var noteTypeFieldNames = noteType.getFields();
    return IntStream.range(0, noteTypeFieldNames.size())
        .boxed()
        .collect(Collectors.toMap(noteTypeFieldNames::get, note.getFlds()::get));
  }
}
