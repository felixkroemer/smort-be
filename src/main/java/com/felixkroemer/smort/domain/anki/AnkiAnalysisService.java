package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.config.SmortProperties;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.common.util.TransactionUtil;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnkiAnalysisEntity;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnkiAnalysisRepository;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnkiAnalysisStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnkiAnalysisService {

  private final AnkiAnalysisRepository ankiAnalysisRepository;

  private final SmortProperties smortProperties;

  @Transactional
  public UUID createAnalysis() {
    var analysis = new AnkiAnalysisEntity(AnkiAnalysisStatus.NEW);
    ankiAnalysisRepository.save(analysis);
    TransactionUtil.afterCommit(
        () -> log.info("Started new Anki analysis. id={}", analysis.getId()));
    return analysis.getId();
  }

  @Transactional
  public void uploadDB(UUID analysisId, byte[] bytes) {
    var ankiAnalysis =
        ankiAnalysisRepository
            .findById(analysisId)
            .orElseThrow(
                () -> new SmortException("Could not find analysis by id. id={}", analysisId));

    if (bytes == null || bytes.length == 0) {
      throw new SmortException("Empty upload for analysis. id={}", analysisId);
    }
    if (bytes.length > smortProperties.getAnkiAnalysisMaxDbSize()) {
      throw new SmortException("Anki DB upload too large. id={}", analysisId);
    }

    if (ankiAnalysis.getStatus() != AnkiAnalysisStatus.NEW) {
      throw new SmortException(
          "Anki Analysis is not in NEW state. id={}, status={}",
          analysisId,
          ankiAnalysis.getStatus());
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

    ankiAnalysis.setDbPath(dbPath.toString());
    ankiAnalysis.setStatus(AnkiAnalysisStatus.READY);

    TransactionUtil.afterCommit(
        () ->
            log.info(
                "Upload complete for Anki analysis. id={}, size={}KB",
                analysisId,
                bytes.length / 1024.0));
  }
}
