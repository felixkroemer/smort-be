package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.config.SmortProperties;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.AnalysisEntityMapper;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
public class AnalysisService {

  private final AnalysisMetaRepository analysisMetaRepository;
  private final BulkFormatRepository bulkFormatRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final DerivedNoteRepository derivedNoteRepository;

  private final SmortProperties smortProperties;

  private final AnalysisEntityMapper analysisEntityMapper;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;

  public UUID createAnalysis() {
    var analysis = new AnalysisMetaEntity(UUID.randomUUID(), AnalysisStatus.NEW);
    analysisMetaRepository.save(analysis);
    log.info("Started new analysis. id={}", analysis.getAnalysisId());
    return analysis.getAnalysisId();
  }

  public Analysis getAnalysis(UUID analysisId) {
    var bulkFormat =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .map(bulkFormatEntityMapper::toBulkFormat);
    return analysisEntityMapper.toAnalysis(getMeta(analysisId), bulkFormat);
  }

  public List<Analysis> getAnalyses() {
    return analysisMetaRepository.findAllAnalysisMetas().stream()
        .map(
            entity ->
                analysisEntityMapper.toAnalysis(
                    entity,
                    bulkFormatRepository
                        .findBulkFormatByAnalysisId(entity.getAnalysisId())
                        .map(bulkFormatEntityMapper::toBulkFormat)))
        .toList();
  }

  public void uploadDB(UUID analysisId, byte[] bytes) {
    var analysis = getMeta(analysisId);

    if (bytes == null || bytes.length == 0) {
      throw new SmortException("Empty upload for analysis. id={}", analysisId);
    }
    if (bytes.length > smortProperties.getAnalysisMaxDbSize()) {
      throw new SmortException("Anki DB upload too large. id={}", analysisId);
    }

    if (analysis.getStatus() != AnalysisStatus.NEW) {
      throw new SmortException(
          "Analysis is not in NEW state. id={}, status={}", analysisId, analysis.getStatus());
    }

    var dbPath = smortProperties.getAnkiDbDirectory().resolve(analysisId.toString());
    try {
      Files.createDirectories(dbPath.getParent());
      Files.write(dbPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (IOException e) {
      throw new SmortException("Failed to write db. id={}, path={}", analysisId, dbPath);
    }

    try {
      analysis.setDbPath(dbPath.toString());
      analysis.setStatus(AnalysisStatus.DB_UPLOADED);
      analysisMetaRepository.save(analysis);
    } catch (Exception e) {
      try {
        log.warn(
            "Failed to persist analysis meta, deleting uploaded db. id={}, db={}",
            analysisId,
            dbPath);
        Files.deleteIfExists(dbPath);
      } catch (Exception cleanupException) {
        log.error("Failed to delete db after save failure. id={}", analysisId, cleanupException);
      }
      throw e;
    }

    log.info("Upload complete for analysis. id={}, size={}KB", analysisId, bytes.length / 1024.0);
  }

  public void setDeck(UUID analysisId, Long deckId) {
    var analysis = getMeta(analysisId);

    if (analysis.getStatus() != AnalysisStatus.DB_UPLOADED) {
      throw new SmortException(
          "Analysis is not in DB_UPLOADED state. id={}, status={}",
          analysisId,
          analysis.getStatus());
    }

    var deck =
        getDecks(analysisId).stream()
            .filter(d -> d.getId().equals(deckId))
            .findAny()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Deck not found. id={}, deckId={}", analysisId, deckId));

    analysis.setStatus(AnalysisStatus.DECK_SELECTED);
    analysis.setDeckId(deckId);
    analysis.setDeckName(deck.getName());
    analysisMetaRepository.save(analysis);
  }

  public List<AnkiNote> getNotes(UUID analysisId) {
    var analysis = getAnalysis(analysisId);

    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    return notes.stream()
        .map(
            n -> {
              var noteType = noteTypes.get(n.getNoteTypeId());
              var noteTypeFieldNames = noteType.getFields();
              var fields =
                  IntStream.range(0, noteTypeFieldNames.size())
                      .boxed()
                      .collect(Collectors.toMap(noteTypeFieldNames::get, n.getFlds()::get));
              return new AnkiNote(n.getId(), fields, n.getGuid(), n.getNoteTypeId());
            })
        .toList();
  }

  public List<AnkiDeckEntity> getDecks(UUID analysisId) {
    return ankiNoteRepository.findDecksByAnalysisId(analysisId);
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
    try {
      if (analysis.getDbPath() != null) {
        Files.deleteIfExists(Path.of(analysis.getDbPath()));
      }
      // TODO: bulk
      derivedNoteRepository.deleteAnalysisDerivedNotes(analysisId);
      bulkFormatRepository.delete(analysisId);
      analysisMetaRepository.delete(analysisId);
    } catch (Exception e) {
      log.warn("Could not fully delete analysis. analysisId={}", analysisId, e);
    }
  }

  private AnalysisMetaEntity getMeta(UUID analysisId) {
    return analysisMetaRepository
        .findAnalysisMetaByAnalysisId(analysisId)
        .orElseThrow(
            () -> new NotFoundException("Could not find analysis by id. id={}", analysisId));
  }
}
