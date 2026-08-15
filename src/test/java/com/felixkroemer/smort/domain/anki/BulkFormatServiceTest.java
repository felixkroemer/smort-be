package com.felixkroemer.smort.domain.anki;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatEntityMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

  private void stubInlineExecutor() {
    doAnswer(
            inv -> {
              Runnable r = inv.getArgument(0);
              r.run();
              return null;
            })
        .when(bulkFormatTaskExecutor)
        .execute(any(Runnable.class));
  }

  @Test
  void cancelBulkFormatWritesCancelledAndCancelsTrackedFuture() {
    var analysisId = UUID.randomUUID();
    var futureTaskRef = new AtomicReference<FutureTask<?>>();
    doAnswer(
            inv -> {
              futureTaskRef.set((FutureTask<?>) inv.getArgument(0));
              return null;
            })
        .when(bulkFormatTaskExecutor)
        .execute(any(Runnable.class));
    var inProgressJob = new BulkFormatEntity(analysisId, true);
    inProgressJob.setStatus(BulkFormatStatus.IN_PROGRESS);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(inProgressJob));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());

    bulkFormatService.startBulkFormat(analysisId, true);
    var futureTask = futureTaskRef.get();
    assertNotNull(futureTask);
    bulkFormatService.cancelBulkFormat(analysisId);
    bulkFormatService.cancelBulkFormat(analysisId);

    assertTrue(futureTask.isCancelled());
    assertTrue(futureTask.isDone());
    futureTask.run();
    verify(chatService, never()).formatNote(any(), any());
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
  void taskStartGuardAbortsCancelledJob() {
    stubInlineExecutor();
    var analysisId = UUID.randomUUID();
    var cancelledJob = new BulkFormatEntity(analysisId, true);
    cancelledJob.setStatus(BulkFormatStatus.CANCELLED);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.of(cancelledJob));

    bulkFormatService.resumeBulkFormat(cancelledJob);

    verify(analysisService, never()).getAnalysis(any());
    verify(noteTypeService, never()).getNoteTypesByAnalysisId(any());
    verify(chatService, never()).formatNote(any(), any());
    verify(bulkFormatRepository, never()).save(any());
  }

  @Test
  void interruptStopsLoopWithoutWritingTerminalStatus() {
    stubInlineExecutor();
    var analysisId = UUID.randomUUID();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(new BulkFormatEntity(analysisId, true)));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L), note(2L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("front", "back"));
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId)).thenReturn(java.util.Map.of(100L, noteType));
    when(chatService.formatNote(any(), any())).thenReturn(new NoteSchema("f2", "b2"));

    Thread.currentThread().interrupt();
    try {
      bulkFormatService.startBulkFormat(analysisId, true);
    } finally {
      Thread.interrupted();
    }

    verify(chatService, never()).formatNote(any(), any());
    verify(derivedNoteRepository, never()).save(any());
    verify(bulkFormatRepository, never())
        .save(
            argThat(
                job ->
                    job.getStatus() == BulkFormatStatus.COMPLETED
                        || job.getStatus() == BulkFormatStatus.FAILED
                        || job.getStatus() == BulkFormatStatus.WAITING_RETRY));
  }

  @Test
  void realExecutorInterruptBreaksConsecutiveFailLoopWithoutWritingTerminalStatus()
      throws InterruptedException {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(Integer.MAX_VALUE);
    executor.setThreadNamePrefix("bulk-format-test-");
    executor.afterPropertiesSet();
    var service =
        new BulkFormatService(
            bulkFormatRepository,
            derivedNoteRepository,
            ankiNoteRepository,
            noteTypeService,
            analysisService,
            chatService,
            bulkFormatEntityMapper,
            executor);

    var analysisId = UUID.randomUUID();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(new BulkFormatEntity(analysisId, true)));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L), note(2L, 100L), note(3L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("front", "back"));
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId))
        .thenReturn(java.util.Map.of(100L, noteType));
    var started = new CountDownLatch(1);
    when(chatService.formatNote(any(), any()))
        .thenAnswer(
            inv -> {
              started.countDown();
              try {
                new CountDownLatch(1).await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
              }
              throw new AssertionError("formatNote should have been interrupted");
            });

    try {
      service.startBulkFormat(analysisId, true);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      service.cancelBulkFormat(analysisId);

      var threadPool = executor.getThreadPoolExecutor();
      var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      int activeCount;
      do {
        activeCount = threadPool.getActiveCount();
        if (activeCount == 0) {
          break;
        }
        Thread.sleep(50);
      } while (System.nanoTime() < deadline);
      assertEquals(0, activeCount);
    } finally {
      executor.shutdown();
    }

    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.CANCELLED));
    verify(bulkFormatRepository, never())
        .save(
            argThat(
                job ->
                    job.getStatus() == BulkFormatStatus.COMPLETED
                        || job.getStatus() == BulkFormatStatus.FAILED
                        || job.getStatus() == BulkFormatStatus.WAITING_RETRY));
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

  @Test
  void interruptSetDuringConsecutiveFailuresDoesNotWriteWaitingRetry() {
    stubInlineExecutor();
    var analysisId = UUID.randomUUID();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId))
        .thenReturn(Optional.empty(), Optional.of(new BulkFormatEntity(analysisId, true)));
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L))
        .thenReturn(List.of(note(1L, 100L), note(2L, 100L)));
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("front", "back"));
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId))
        .thenReturn(java.util.Map.of(100L, noteType));
    when(chatService.formatNote(any(), any()))
        .thenThrow(new RuntimeException("boom"))
        .thenAnswer(
            inv -> {
              Thread.currentThread().interrupt();
              throw new RuntimeException("boom");
            });

    try {
      bulkFormatService.startBulkFormat(analysisId, true);
    } finally {
      Thread.interrupted();
    }

    verify(bulkFormatRepository, never())
        .save(
            argThat(
                job ->
                    job.getStatus() == BulkFormatStatus.WAITING_RETRY
                        || job.getStatus() == BulkFormatStatus.COMPLETED
                        || job.getStatus() == BulkFormatStatus.FAILED));
  }
}
