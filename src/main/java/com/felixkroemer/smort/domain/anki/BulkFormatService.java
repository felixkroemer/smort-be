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

  public void startBulkFormat(UUID analysisId) {
    var existing = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.PENDING) {
        throw new SmortException(
            "Bulk format already in progress for analysis. analysisId={}", analysisId);
      }
      if (job.getStatus() == BulkFormatStatus.COMPLETED) {
        throw new SmortException(
            "Bulk format already completed for analysis. analysisId={}", analysisId);
      }
    }

    var analysis = analysisService.getAnalysis(analysisId);
    var totalNotes =
        ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId()).size();

    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setTotalNotes(totalNotes);
    bulkFormatRepository.save(job);

    processNotes(analysisId, job);
  }

  public void resumeBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new SmortException("No bulk format job found. analysisId={}", analysisId));

    job.setLastUpdatedAt(Instant.now());
    bulkFormatRepository.save(job);

    processNotes(analysisId, job);
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

    job.setAttempts(attempts);
    job.setCompletedNotes(existingDerivedNotes.size());
    job.setTotalNotes(notes.size());
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
        bulkFormatRepository.save(job);

      } catch (Exception e) {
        failed++;
        consecutiveFailed++;
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
