package com.felixkroemer.smort.domain.anki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkFormatServiceTest {

  @Mock BulkFormatRepository bulkFormatRepository;
  @Mock DerivedNoteRepository derivedNoteRepository;
  @Mock AnkiNoteRepository ankiNoteRepository;
  @Mock AnkiNoteTypeService noteTypeService;
  @Mock AnalysisService analysisService;
  @Mock ChatService chatService;
  @Mock BulkFormatEntityMapper bulkFormatEntityMapper;
  @Mock AsyncTaskExecutor bulkFormatTaskExecutor;

  BulkFormatService bulkFormatService;

  @BeforeEach
  void setUp() {
    bulkFormatService =
        new BulkFormatService(
            bulkFormatRepository,
            derivedNoteRepository,
            ankiNoteRepository,
            noteTypeService,
            analysisService,
            chatService,
            bulkFormatEntityMapper,
            bulkFormatTaskExecutor);
  }

  private Analysis analysis() {
    var analysis = new Analysis();
    analysis.setDeckId(1L);
    analysis.setFormatInstructions(Optional.of("instructions"));
    return analysis;
  }

  private AnkiNoteEntity note(Long id, Long noteTypeId) {
    var note = mock(AnkiNoteEntity.class);
    when(note.getId()).thenReturn(id);
    when(note.getNoteTypeId()).thenReturn(noteTypeId);
    when(note.getFlds()).thenReturn(List.of("front", "back"));
    return note;
  }

  @Test
  void cancelBulkFormatOnInProgressJobPersistsCancelled() {
    var analysisId = UUID.randomUUID();
    var inProgressJob = new BulkFormatEntity(analysisId, true);
    inProgressJob.setStatus(BulkFormatStatus.IN_PROGRESS);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(inProgressJob));

    bulkFormatService.cancelBulkFormat(analysisId);

    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.CANCELLED));
  }

  @Test
  void cancelBulkFormatOnCompletedJobIsNoop() {
    var analysisId = UUID.randomUUID();
    var completedJob = new BulkFormatEntity(analysisId, true);
    completedJob.setStatus(BulkFormatStatus.COMPLETED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(completedJob));

    bulkFormatService.cancelBulkFormat(analysisId);

    verify(bulkFormatRepository, never()).save(any());
  }

  @Test
  void cancelBulkFormatOnMissingJobThrowsNotFoundException() {
    when(bulkFormatRepository.findBulkFormatByAnalysisId(any())).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> bulkFormatService.cancelBulkFormat(UUID.randomUUID()));
  }

  @Test
  void canRestartAfterCancel() {
    var analysisId = UUID.randomUUID();
    var cancelledJob = new BulkFormatEntity(analysisId, true);
    cancelledJob.setStatus(BulkFormatStatus.CANCELLED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(cancelledJob));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());

    assertDoesNotThrow(() -> bulkFormatService.startBulkFormat(analysisId, true));

    verify(bulkFormatRepository).save(argThat(job -> job.getStatus() == BulkFormatStatus.PENDING));
  }

  @Test
  void cancelBulkFormatOnWaitingRetryJobPersistsCancelled() {
    var analysisId = UUID.randomUUID();
    var waitingJob = new BulkFormatEntity(analysisId, true);
    waitingJob.setStatus(BulkFormatStatus.WAITING_RETRY);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(waitingJob));

    bulkFormatService.cancelBulkFormat(analysisId);

    verify(bulkFormatRepository).findBulkFormatByAnalysisId(analysisId);
    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.CANCELLED));
  }
}
