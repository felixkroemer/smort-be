package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.LogSeverity;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.common.BulkFormatEngine;
import com.felixkroemer.smort.domain.common.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkFormatService {

  private final BulkFormatRepository bulkFormatRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnalysisService analysisService;
  private final ChatService chatService;
  private final BulkFormatEntityMapper bulkFormatEntityMapper;
  private final DerivedNoteEntityMapper derivedNoteEntityMapper;
  private final BulkFormatEngine bulkFormatEngine;

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

    var job = new AnalysisBulkFormatEntity(analysisId, reformatAlreadyFormatted);
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
    bulkFormatEngine.dispatch(job, () -> processNotes(job, notesToProcess));
  }

  public void resumeBulkFormat(AnalysisBulkFormatEntity bulkFormatEntity) {
    bulkFormatEngine.dispatch(bulkFormatEntity, () -> processNotes(bulkFormatEntity));
  }

  public void cancelBulkFormat(UUID analysisId) {
    var job =
        bulkFormatRepository
            .findBulkFormatByAnalysisId(analysisId)
            .orElseThrow(
                () -> new NotFoundException("No bulk format job found. analysisId={}", analysisId));
    bulkFormatEngine.cancel(job);
  }

  private void processNotes(AnalysisBulkFormatEntity job) {
    var notes = analysisService.getNotes(job.getAnalysisId());
    var existingDerivedNotes =
        analysisService.getDerivedNotes(job.getAnalysisId()).stream()
            .collect(
                Collectors.toMap(
                    DerivedNoteEntity::getNoteId, Function.identity(), (first, second) -> first));
    var notesToProcess = getNotesToProcess(notes, existingDerivedNotes, job);
    processNotes(job, notesToProcess);
  }

  private void processNotes(
      AnalysisBulkFormatEntity job, List<NoteToProcess> notesToProcess) {
    var analysisId = job.getAnalysisId();
    Analysis analysis;
    try {
      analysis = analysisService.getAnalysis(analysisId);
    } catch (NotFoundException e) {
      throw e.withSeverity(LogSeverity.ERROR);
    }

    bulkFormatEngine.process(
        job,
        notesToProcess,
        noteToProcess -> {
          var noteEntity = noteToProcess.ankiNote();
          var existingDerivedNote = noteToProcess.existingDerivedNote();
          var content =
              existingDerivedNote
                  .map(DerivedNoteEntity::getContent)
                  .orElse(noteEntity.getContent());
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
        });
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
