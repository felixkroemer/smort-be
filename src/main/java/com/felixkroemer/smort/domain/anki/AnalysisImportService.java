package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.config.SmortProperties;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisStatus;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiDeckEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisImportService {

  private final AnalysisMetaRepository analysisMetaRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnalysisService analysisService;

  private final SmortProperties smortProperties;

  public UUID createAnalysis() {
    var analysis = new AnalysisMetaEntity(UUID.randomUUID(), "default", AnalysisStatus.NEW);
    analysisMetaRepository.save(analysis);
    log.info("Started new analysis. id={}", analysis.getAnalysisId());
    return analysis.getAnalysisId();
  }

  public void uploadDB(UUID analysisId, byte[] bytes) {
    var analysis = analysisService.getMeta(analysisId);

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

  public List<AnkiDeckEntity> getDecks(UUID analysisId) {
    return ankiNoteRepository.findDecksByAnalysisId(analysisId);
  }

  public void setDeck(UUID analysisId, Long deckId) {
    var analysis = analysisService.getMeta(analysisId);

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
                    new NotFoundException("Deck not found. id={}, deckId={}", analysisId, deckId));

    analysis.setStatus(AnalysisStatus.DECK_SELECTED);
    analysis.setDeckId(deckId);
    analysis.setDeckName(deck.getName());
    analysis.setNoteCount(deck.getCards().size());
    analysisMetaRepository.save(analysis);
  }
}
