package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.config.SmortProperties;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.common.util.TransactionUtil;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisEntity;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisRepository;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisStatus;
import com.felixkroemer.smort.infrastructure.sqlite.anki.*;
import java.io.IOException;
import java.nio.file.Files;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

  private final AnalysisRepository analysisRepository;
  private final NoteRepository noteRepository;
  private final NoteTypeService noteTypeService;
  private final DerivedNoteRepository derivedNoteRepository;

  private final SmortProperties smortProperties;

  @Transactional
  public UUID createAnalysis() {
    var analysis = new AnalysisEntity(AnalysisStatus.NEW);
    analysisRepository.save(analysis);
    TransactionUtil.afterCommit(() -> log.info("Started new analysis. id={}", analysis.getId()));
    return analysis.getId();
  }

  @Transactional
  public void uploadDB(UUID analysisId, byte[] bytes) {
    var analysis =
        analysisRepository
            .findById(analysisId)
            .orElseThrow(
                () -> new SmortException("Could not find analysis by id. id={}", analysisId));

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
      TransactionUtil.afterRollback(
          () -> {
            try {
              log.warn(
                  "Tx rolled back. Attempting to deleted potentially uploaded db. id={}, db={}",
                  analysisId,
                  dbPath);
              Files.deleteIfExists(dbPath);
            } catch (Exception e) {
              log.error("Failed to delete db after rollback. id={}", analysisId, e);
            }
          });
    } catch (IOException e) {
      throw new SmortException(e);
    }

    analysis.setDbPath(dbPath.toString());
    analysis.setStatus(AnalysisStatus.READY);

    TransactionUtil.afterCommit(
        () ->
            log.info(
                "Upload complete for analysis. id={}, size={}KB",
                analysisId,
                bytes.length / 1024.0));
  }

  public List<Note> getNotes(UUID analysisId, Long deckId) {
    var noteTypes = noteTypeService.getNoteTypes(analysisId);
    var notes = noteRepository.findNotesByDeck(analysisId, deckId);
    return notes.stream()
        .map(
            n -> {
              var noteType = noteTypes.get(n.getNoteTypeId());
              var noteTypeFieldNames = noteType.getFields();
              var fields =
                  IntStream.range(0, noteTypeFieldNames.size())
                      .boxed()
                      .collect(Collectors.toMap(noteTypeFieldNames::get, n.getFlds()::get));
              return new Note(n.getId(), n.getCards(), fields, n.getGuid());
            })
        .toList();
  }

  public List<DeckEntity> getDecks(UUID analysisId) {
    return noteRepository.findAllDecks(analysisId);
  }

  public List<DerivedNoteEntity> getDerivedNotes(UUID analysisId, Long deckId) {
    return derivedNoteRepository.findAllByDeckId(analysisId, deckId);
  }

  public Map<DerivedNoteEntity, String> getDerivedNoteToGuidMapping(
      UUID analysisId, List<DerivedNoteEntity> derivedNotes) {
    var derivedNoteIds =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var guidByNoteId =
        noteRepository.findNotesByIdIn(analysisId, derivedNoteIds).stream()
            .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getGuid));

    return derivedNotes.stream()
        .collect(Collectors.toMap(Function.identity(), d -> guidByNoteId.get(d.getNoteId())));
  }

  public List<DerivedNoteEntity> getAllDerivedNotes(UUID analysisId) {
    return derivedNoteRepository.findAll(analysisId);
  }
}
