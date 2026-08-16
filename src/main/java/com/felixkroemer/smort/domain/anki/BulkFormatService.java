package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatService {

  public static final int MAX_RECENT_FAILED = 2;
  public static final int MAX_ATTEMPTS = 2;

  private final BulkFormatRepository bulkFormatRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnalysisService analysisService;
  private final ChatService chatService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DerivedNoteEntityMapper derivedNoteEntityMapper;
  private final AsyncTaskExecutor bulkFormatTaskExecutor;

  public void startBulkFormat(UUID analysisId, boolean reformatAlreadyFormatted) {
    var existing = bulkFormatRepository.findBulkFormatByAnalysisId(analysisId);
    if (existing.isPresent()) {
      var job = existing.get();
      if (job.getStatus() == BulkFormatStatus.PENDING
          || job.getStatus() == BulkFormatStatus.IN_PROGRESS
          || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
        throw new SmortException(
            "Bulk format already in progress for analysis. analysisId={}", analysisId);
      }
    }

    var notes = analysisService.getNotes(analysisId);
    var existingDerivedNotes =
        analysisService.getDerivedNotes(analysisId).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));

    var job = new BulkFormatEntity(analysisId, reformatAlreadyFormatted);
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);

    if (notesToProcess.isEmpty()) {
      throw new SmortException(
          HttpStatus.BAD_REQUEST,
          LogSeverity.INFO,
          "No notes to format for analysis. analysisId={}",
          analysisId);
    }

    job.setTotalNotes(notesToProcess.size());
    bulkFormatRepository.save(job);
    dispatch(job.getAnalysisId(), () -> processNotes(job, notesToProcess));
  }

  public void resumeBulkFormat(BulkFormatEntity bulkFormatEntity) {
    dispatch(bulkFormatEntity.getAnalysisId(), () -> processNotes(bulkFormatEntity));
  }

  public void cancelBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
    if (job.getStatus() == BulkFormatStatus.PENDING
        || job.getStatus() == BulkFormatStatus.IN_PROGRESS
        || job.getStatus() == BulkFormatStatus.WAITING_RETRY) {
      job.setStatus(BulkFormatStatus.CANCELLED);
      bulkFormatRepository.save(job);
    }
  }

  private void dispatch(UUID analysisId, Runnable task) {
    bulkFormatTaskExecutor.execute(
        () -> {
          try {
            task.run();
          } catch (BulkFormatCancelledException e) {
            log.info("Bulk format cancelled. analysisId={}", analysisId);
          } catch (Exception e) {
            log.error(
                "Unexpected error during bulk format processing. analysisId={}", analysisId, e);
          }
        });
  }

  private void processNotes(BulkFormatEntity job) {
    var notes = analysisService.getNotes(job.getAnalysisId());
    var existingDerivedNotes =
        analysisService.getDerivedNotes(job.getAnalysisId()).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess) {
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }

    int processed = 0;
    int failed = 0;
    int consecutiveFailed = 0;
    int attempts = job.getAttempts() + 1;

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setAttempts(attempts);
    bulkFormatRepository.save(job);

    for (var noteToProcess : notesToProcess) {
      var noteEntity = noteToProcess.ankiNote();
      var existingDerivedNote = noteToProcess.existingDerivedNote();
      try {
        var content =
            existingDerivedNote.map(DerivedNoteEntity::getContent).orElse(noteEntity.getContent());
        var noteSchema = chatService.formatNote(content, analysis.getFormatInstructions());
        var derivedNote =
            existingDerivedNote
                .map(
                    d -> {
                      d.setFront(noteSchema.front());
                      d.setBack(noteSchema.back());
                      d.setLastFormattedAt(Optional.of(Instant.now()));
                      return d;
                    })
                .orElseGet(
                    () ->
                        derivedNoteEntityMapper.toDerivedNoteEntity(
                            analysisId, noteEntity.getId(), noteSchema));
        derivedNoteRepository.save(derivedNote);

        processed++;
        consecutiveFailed = 0;
        job.setCompletedNotes(job.getCompletedNotes() + 1);

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
        continue;
      }

      job.setLastUpdatedAt(Instant.now());
      bulkFormatRepository.save(job);
    }

    handleProcessNotesResult(job, processed, failed, analysisId);
  }

  private void handleProcessNotesResult(
      BulkFormatEntity job, int processed, int failed, UUID analysisId) {
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
        job.setStatus(BulkFormatStatus.WAITING_RETRY);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);
        log.info(
            "Bulk format had errors. Will resume later.  analysisId={}, processed={}, failed={}, attempts={}",
            analysisId,
            processed,
            failed,
            job.getAttempts());
      }
    }
  }

  private List<NoteToProcess> getNotesToProcess(
      List<AnkiNote> notes,
      Map<Long, DerivedNoteEntity> existingDerivedNotes,
      BulkFormatEntity job) {
    return notes.stream()
        .filter(
            note -> {
              var derivedNote = existingDerivedNotes.get(note.getId());
              if (derivedNote == null) {
                return true;
              }
              if (!job.isReformatAlreadyFormatted()) {
                return false;
              }
              return derivedNote
                  .getLastFormattedAt()
                  .map(lastFormattedAt -> lastFormattedAt.isBefore(job.getCreatedAt()))
                  .orElse(true);
            })
        .map(
            note ->
                new NoteToProcess(
                    note, Optional.ofNullable(existingDerivedNotes.get(note.getId()))))
        .toList();
  }

  private record NoteToProcess(
      AnkiNote ankiNote, Optional<DerivedNoteEntity> existingDerivedNote) {}

  public BulkFormat getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(bulkFormatEntityMapper::toBulkFormat)
        .orElseThrow(
            () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
  }
}
