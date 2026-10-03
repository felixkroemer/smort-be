package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.AnalysisEntityMapper;
import com.felixkroemer.smort.domain.common.FormattingMode;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

  private final AnalysisMetaRepository analysisMetaRepository;
  private final BulkFormatRepository bulkFormatRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final DerivedNoteRepository derivedNoteRepository;

  private final AnalysisEntityMapper analysisEntityMapper;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;

  public Analysis getAnalysis(UUID analysisId) {
    var bulkFormat =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .map(bulkFormatEntityMapper::toBulkFormat);
    return analysisEntityMapper.toAnalysis(getMeta(analysisId), bulkFormat);
  }

  public List<Analysis> getAnalyses() {
    return analysisMetaRepository.findAnalysisMetasByUserId("default").stream()
        .map(
            entity ->
                analysisEntityMapper.toAnalysis(
                    entity,
                    bulkFormatRepository
                        .findBulkFormatByAnalysisId(entity.getAnalysisId())
                        .map(bulkFormatEntityMapper::toBulkFormat)))
        .toList();
  }

  public AnalysisSettings getAnalysisSettings(UUID analysisId) {
    var meta = getMeta(analysisId);
    return new AnalysisSettings(meta.getFormattingMode(), meta.getTemplateId(), meta.getFormatInstructions());
  }

  public AnalysisSettings updateAnalysisSettings(
      UUID analysisId,
      FormattingMode formattingMode,
      String templateId,
      String formatInstructions) {
    var analysis = getMeta(analysisId);
    if (formattingMode != null) analysis.setFormattingMode(formattingMode);
    if (templateId != null) analysis.setTemplateId(templateId);
    if (formatInstructions != null) analysis.setFormatInstructions(formatInstructions);
    if (formattingMode != null || templateId != null || formatInstructions != null) {
      analysis.setUpdatedAt(Instant.now());
      analysisMetaRepository.save(analysis);
    }
    return new AnalysisSettings(analysis.getFormattingMode(), analysis.getTemplateId(), analysis.getFormatInstructions());
  }

  public List<AnkiNote> getNotes(UUID analysisId) {
    var analysis = getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    return notes.stream()
        .map(
            n ->
                new AnkiNote(
                    n.getId(),
                    noteTypeService.getContent(analysisId, n),
                    n.getGuid(),
                    n.getNoteTypeId()))
        .toList();
  }

  public List<DerivedNoteEntity> getDerivedNotes(UUID analysisId) {
    return derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId);
  }

  public List<AnkiNoteTypeEntity> getNoteTypes(UUID analysisId) {
    var notes = getNotes(analysisId);
    var deckNoteTypeIds = notes.stream().map(AnkiNote::getNoteTypeId).collect(Collectors.toSet());
    var allNoteTypes = ankiNoteRepository.findNoteTypesByAnalysisId(analysisId);
    return allNoteTypes.stream()
        .filter(noteType -> deckNoteTypeIds.contains(noteType.getId()))
        .toList();
  }

  public Map<DerivedNoteEntity, String> getDerivedNoteToGuidMapping(
      UUID analysisId, List<DerivedNoteEntity> derivedNotes) {
    var derivedNoteIds =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var guidByNoteId =
        ankiNoteRepository.findNotesByAnalysisIdAndNoteIdIn(analysisId, derivedNoteIds).stream()
            .collect(Collectors.toMap(AnkiNoteEntity::getId, AnkiNoteEntity::getGuid));

    return derivedNotes.stream()
        .collect(Collectors.toMap(Function.identity(), d -> guidByNoteId.get(d.getNoteId())));
  }

  public void deleteAnalysis(UUID analysisId) {
    var analysis = getMeta(analysisId);
    analysis.setStatus(AnalysisStatus.MARKED_FOR_DELETION);
    analysisMetaRepository.save(analysis);
  }

  private AnalysisMetaEntity getMeta(UUID analysisId) {
    return analysisMetaRepository
        .findAnalysisMetaByAnalysisId(analysisId)
        .orElseThrow(
            () -> new NotFoundException("Could not find analysis by id. id={}", analysisId));
  }
}
