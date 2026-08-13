package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatService {

  public static final int MAX_RECENT_FAILED = 2;
  public static final int MAX_ATTEMPTS = 2;
  private final BulkFormatRepository bulkFormatRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final ChatService chatService;
  private final BulkFormatMapper bulkFormatMapper;
  private final TaskExecutor bulkFormatTaskExecutor;

  public void startBulkFormat(UUID analysisId) {
    var existing = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.PENDING
          || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
        throw new SmortException(
            "Bulk format already in progress for analysis. analysisId={}", analysisId);
      }
      if (job.getStatus() == BulkFormatStatus.COMPLETED) {
        throw new SmortException(
            "Bulk format already completed for analysis. analysisId={}", analysisId);
      }
    }
    var job = new BulkFormatEntity(analysisId);
    bulkFormatRepository.save(job);
    dispatch(analysisId, job);
  }

  public void resumeBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
    dispatch(analysisId, job);
  }

  private void dispatch(UUID analysisId, BulkFormatEntity job) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            processNotes(analysisId, job);
          } catch (Exception e) {
            log.error("Unexpected error during bulk format processing. analysisId={}", analysisId, e);
          }
        });
  }

  private void processNotes(UUID analysisId, BulkFormatEntity job) {
    var analysis = analysisService.getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);

    var existingDerivedNotes =
        derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId).stream()
            .map(DerivedNoteEntity::getNoteId)
            .collect(Collectors.toSet());

    var notesToProcess =
        notes.stream().filter(note -> !existingDerivedNotes.contains(note.getId())).toList();

    int processed = 0;
    int failed = 0;
    int consecutiveFailed = 0;
    int attempts = job.getAttempts() + 1;

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setAttempts(attempts);
    job.setCompletedNotes(existingDerivedNotes.size());
    job.setTotalNotes(notes.size());
    job.setFailedCount(0);
    bulkFormatRepository.save(job);

    for (var noteEntity : notesToProcess) {
      try {
        var noteType = noteTypes.get(noteEntity.getNoteTypeId());
        var typeFieldNames = noteType.getFields();
        var content =
            IntStream.range(0, typeFieldNames.size())
                .boxed()
                .collect(Collectors.toMap(typeFieldNames::get, noteEntity.getFlds()::get));

        var noteSchema = chatService.formatNote(content);
        var derivedNote =
            new DerivedNoteEntity(
                analysisId, noteEntity.getId(), noteSchema.getFront(), noteSchema.getBack());
        derivedNote.setLastFormattedAt(Optional.of(Instant.now()));
        derivedNoteRepository.save(derivedNote);

        processed++;
        consecutiveFailed = 0;
        job.setCompletedNotes(job.getCompletedNotes() + 1);
        job.setLastUpdatedAt(Instant.now());
        job.setFailedCount(failed);
        bulkFormatRepository.save(job);

      } catch (Exception e) {
        failed++;
        consecutiveFailed++;
        job.setFailedCount(failed);
        log.warn(
            "Failed to format note during bulk format. analysisId={}, noteId={}",
            analysisId,
            noteEntity.getId(),
            e);
        if (consecutiveFailed >= MAX_RECENT_FAILED) {
          log.warn(
              "Hit consecutive failed limit while processing bulk format. analysisId={}",
              analysisId);
          break;
        }
      }
    }

    if (failed == 0) {
      job.setStatus(BulkFormatStatus.COMPLETED);
      job.setLastUpdatedAt(Instant.now());
      job.setFailedCount(0);
      bulkFormatRepository.save(job);

      log.info(
          "Bulk format complete. analysisId={}, processed={}, failed={}",
          analysisId,
          processed,
          failed);
    } else {
      if (job.getAttempts() >= MAX_ATTEMPTS) {
        job.setStatus(BulkFormatStatus.FAILED);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

        log.warn(
            "Bulk format reached max attempts. Setting to FAILED. analysisId={}, processed={}, failed={}",
            analysisId,
            processed,
            failed);
      } else {
        job.setStatus(BulkFormatStatus.WAITING_RETRY);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

        log.info(
            "Bulk format had errors. Will resume later.  analysisId={}, processed={}, failed={}, attempts={}",
            analysisId,
            processed,
            failed,
            attempts);
      }
    }
  }

  public BulkFormat getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(bulkFormatMapper::toBulkFormat)
        .orElseThrow(
            () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
  }
}
