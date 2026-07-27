package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
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

  private final BulkFormatRepository bulkFormatRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final AnkiNoteRepository ankiNoteRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final ChatService chatService;

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

    job.setStatus(BulkFormatStatus.IN_PROGRESS);
    job.setLastUpdatedAt(Instant.now());
    bulkFormatRepository.save(job);

    processNotes(analysisId, job);
  }

  private void processNotes(UUID analysisId, BulkFormatEntity job) {
    var analysis = analysisService.getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    var noteTypes = noteTypeService.getNoteTypesByAnalysisId(analysisId);

    int failed = 0;

    for (var noteEntity : notes) {
      var existing =
          derivedNoteRepository.finDerivedNotedByAnalysisIdAndNoteId(
              analysisId, noteEntity.getId());
      if (existing.isPresent()) {
        job.setCompletedNotes(job.getCompletedNotes() + 1);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);
        continue;
      }

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
        derivedNoteRepository.save(derivedNote);

        job.setCompletedNotes(job.getCompletedNotes() + 1);
        job.setLastUpdatedAt(Instant.now());
        bulkFormatRepository.save(job);

      } catch (Exception e) {
        failed++;
        log.warn(
            "Failed to format note during bulk format. analysisId={}, noteId={}",
            analysisId,
            noteEntity.getId(),
            e);
      }
    }

    job.setStatus(BulkFormatStatus.COMPLETED);
    job.setLastUpdatedAt(Instant.now());
    bulkFormatRepository.save(job);

    log.info(
        "Bulk format complete. analysisId={}, processed={}, failed={}",
        analysisId,
        job.getCompletedNotes(),
        failed);
  }

  public BulkFormatEntity getJobStatus(UUID analysisId) {
    return bulkFormatRepository
        .findBulkFormatByAnalysisId(analysisId)
        .orElseThrow(
            () -> new SmortException("No bulk format job found. analysisId={}", analysisId));
  }
}
