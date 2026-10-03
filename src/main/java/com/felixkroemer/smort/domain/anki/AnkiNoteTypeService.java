package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
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
  private final AnalysisService analysisService;
  private final Cache<UUID, Map<Long, AnkiNoteTypeEntity>> noteTypeCache =
      Caffeine.newBuilder().build();

  public Map<Long, AnkiNoteTypeEntity> getNoteTypesByAnalysisId(UUID analysisId) {
    return noteTypeCache.get(
        analysisId,
        id ->
            ankiNoteRepository.findNoteTypesByAnalysisId(id).stream()
                .collect(Collectors.toMap(AnkiNoteTypeEntity::getId, Function.identity())));
  }

  public List<AnkiNoteTypeEntity> getNoteTypes(UUID analysisId) {
    var analysis = analysisService.getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var deckNoteTypeIds = notes.stream().map(AnkiNoteEntity::getNoteTypeId).collect(Collectors.toSet());
    var allNoteTypes = ankiNoteRepository.findNoteTypesByAnalysisId(analysisId);
    return allNoteTypes.stream()
        .filter(noteType -> deckNoteTypeIds.contains(noteType.getId()))
        .toList();
  }

  public Map<String, String> getContent(UUID analysisId, AnkiNoteEntity note) {
    var noteType = getNoteTypesByAnalysisId(analysisId).get(note.getNoteTypeId());
    var noteTypeFieldNames = noteType.getFields();
    return IntStream.range(0, noteTypeFieldNames.size())
        .boxed()
        .collect(Collectors.toMap(noteTypeFieldNames::get, note.getFlds()::get));
  }
}
