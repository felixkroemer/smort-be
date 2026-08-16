package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.BulkFormatCancelledException;
import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final ChatService chatService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
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

    var analysis = analysisService.getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var existingDerivedNotes =
        derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId).stream()
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

  private boolean isCancelled(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(job -> job.getStatus() == BulkFormatStatus.CANCELLED)
        .orElse(false);
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
    var analysisId = job.getAnalysisId();
    if (isCancelled(analysisId)) {
      log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
      return;
    }
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var existingDerivedNotes =
        derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(BulkFormatEntity job, List<NoteToProcess> notesToProcess) {
    var analysisId = job.getAnalysisId();
    if (isCancelled(analysisId)) {
      log.info("Bulk format skipped, already cancelled. analysisId={}", analysisId);
      return;
    }
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);

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
        var noteType = noteTypes.get(noteEntity.getNoteTypeId());
        var typeFieldNames = noteType.getFields();
        var content =
            existingDerivedNote
                .map(d -> Map.of("front", d.getFront(), "back", d.getBack()))
                .orElseGet(
                    () ->
                        IntStream.range(0, typeFieldNames.size())
                            .boxed()
                            .collect(
                                Collectors.toMap(typeFieldNames::get, noteEntity.getFlds()::get)));

        var noteSchema = chatService.formatNote(content, analysis.getFormatInstructions());
        var derivedNote =
            existingDerivedNote
                .map(
                    d -> {
                      d.setFront(noteSchema.getFront());
                      d.setBack(noteSchema.getBack());
                      d.setLastFormattedAt(Optional.of(Instant.now()));
                      return d;
                    })
                .orElseGet(
                    () -> {
                      var newNote =
                          new DerivedNoteEntity(
                              analysisId,
                              noteEntity.getId(),
                              noteSchema.getFront(),
                              noteSchema.getBack());
                      newNote.setLastFormattedAt(Optional.of(Instant.now()));
                      return newNote;
                    });
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
      List<AnkiNoteEntity> notes,
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
      AnkiNoteEntity ankiNote, Optional<DerivedNoteEntity> existingDerivedNote) {}

  public BulkFormat getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .map(bulkFormatEntityMapper::toBulkFormat)
        .orElseThrow(
            () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
  }
}
