package com.felixkroemer.smort.domain.anki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.mapping.BulkFormatMapper;
import com.felixkroemer.smort.domain.chat.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

class BulkFormatServiceTest {

  private final UUID analysisId = UUID.randomUUID();
  private BulkFormatRepository bulkFormatRepository;
  private DerivedNoteRepository derivedNoteRepository;
  private AnkiNoteRepository ankiNoteRepository;
  private AnkiNoteTypeService noteTypeService;
  private AnalysisService analysisService;
  private ChatService chatService;
  private BulkFormatMapper bulkFormatMapper;
  private TaskExecutor taskExecutor;
  private BulkFormatService service;

  @BeforeEach
  void setUp() {
    bulkFormatRepository = mock(BulkFormatRepository.class);
    derivedNoteRepository = mock(DerivedNoteRepository.class);
    ankiNoteRepository = mock(AnkiNoteRepository.class);
    noteTypeService = mock(AnkiNoteTypeService.class);
    analysisService = mock(AnalysisService.class);
    chatService = mock(ChatService.class);
    bulkFormatMapper = mock(BulkFormatMapper.class);
    taskExecutor = mock(TaskExecutor.class);
    service =
        new BulkFormatService(
            bulkFormatRepository,
            derivedNoteRepository,
            ankiNoteRepository,
            noteTypeService,
            analysisService,
            chatService,
            bulkFormatMapper,
            taskExecutor);
  }

  /** Makes the mocked TaskExecutor run dispatched Runnables inline. */
  private void runTasksSynchronously() {
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(taskExecutor)
        .execute(any(Runnable.class));
  }

  private Analysis analysis() {
    var analysis = new Analysis();
    analysis.setAnalysisId(analysisId);
    analysis.setDeckId(1L);
    return analysis;
  }

  private AnkiNoteEntity note(long id) {
    var note = mock(AnkiNoteEntity.class);
    when(note.getId()).thenReturn(id);
    when(note.getNoteTypeId()).thenReturn(1L);
    when(note.getFlds()).thenReturn(List.of("field-a", "field-b"));
    return note;
  }

  private AnkiNoteTypeEntity noteType() {
    var noteType = mock(AnkiNoteTypeEntity.class);
    when(noteType.getFields()).thenReturn(List.of("field-a", "field-b"));
    return noteType;
  }

  private void stubFormatNoteSuccess() {
    when(chatService.formatNote(any()))
        .thenAnswer(
            i -> {
              var schema = new ChatService.NoteSchema();
              schema.front = "front";
              schema.back = "back";
              return schema;
            });
  }

  private void stubFormatNoteFailure() {
    when(chatService.formatNote(any())).thenThrow(new SmortException("model refused"));
  }

  private BulkFormatEntity lastSavedJob() {
    ArgumentCaptor<BulkFormatEntity> captor = ArgumentCaptor.forClass(BulkFormatEntity.class);
    verify(bulkFormatRepository, atLeastOnce()).save(captor.capture());
    List<BulkFormatEntity> saved = captor.getAllValues();
    return saved.get(saved.size() - 1);
  }

  private void stubStandardJobSetup() {
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    var notes = List.of(note(1L), note(2L));
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L)).thenReturn(notes);
    when(derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId)).thenReturn(List.of());
    var noteTypes = Map.of(1L, noteType());
    when(noteTypeService.getNoteTypesByAnalysisId(analysisId)).thenReturn(noteTypes);
  }

  @Test
  void startBulkFormatSchedulesProcessingWithoutRunningIt() {
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    when(analysisService.getAnalysis(analysisId)).thenReturn(analysis());
    var notes = List.of(note(1L));
    when(ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, 1L)).thenReturn(notes);

    service.startBulkFormat(analysisId);

    verify(taskExecutor).execute(any(Runnable.class));
    verify(chatService, never()).formatNote(any());
    verify(bulkFormatRepository)
        .save(argThat(job -> job.getStatus() == BulkFormatStatus.IN_PROGRESS));
  }

  @Test
  void startBulkFormatCompletesWhenAllNotesSucceed() {
    runTasksSynchronously();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    stubStandardJobSetup();
    stubFormatNoteSuccess();

    service.startBulkFormat(analysisId);

    var job = lastSavedJob();
    assertThat(job.getStatus()).isEqualTo(BulkFormatStatus.COMPLETED);
    assertThat(job.getCompletedNotes()).isEqualTo(2);
    assertThat(job.getFailedCount()).isZero();
    assertThat(job.getAttempts()).isEqualTo(1);
    verify(derivedNoteRepository, times(2)).save(any(DerivedNoteEntity.class));
  }

  @Test
  void startBulkFormatSetsWaitingRetryOnPartialFailure() {
    runTasksSynchronously();
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.empty());
    stubStandardJobSetup();
    stubFormatNoteFailure();

    service.startBulkFormat(analysisId);

    var job = lastSavedJob();
    assertThat(job.getStatus()).isEqualTo(BulkFormatStatus.WAITING_RETRY);
    assertThat(job.getFailedCount()).isEqualTo(2);
    assertThat(job.getAttempts()).isEqualTo(1);
    verify(derivedNoteRepository, never()).save(any(DerivedNoteEntity.class));
  }

  @Test
  void resumeBulkFormatExhaustsAttemptsToFailed() {
    runTasksSynchronously();
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setAttempts(1);
    job.setTotalNotes(2);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));
    stubStandardJobSetup();
    stubFormatNoteFailure();

    service.resumeBulkFormat(analysisId);

    var finalJob = lastSavedJob();
    assertThat(finalJob.getStatus()).isEqualTo(BulkFormatStatus.FAILED);
    assertThat(finalJob.getAttempts()).isEqualTo(2);
    assertThat(finalJob.getFailedCount()).isEqualTo(2);
  }

  @Test
  void resumeBulkFormatSetsInProgressBeforeDispatching() {
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    job.setAttempts(1);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));

    service.resumeBulkFormat(analysisId);

    verify(bulkFormatRepository)
        .save(argThat(saved -> saved.getStatus() == BulkFormatStatus.IN_PROGRESS));
    verify(taskExecutor).execute(any(Runnable.class));
  }

  @Test
  void startBulkFormatRejectsWaitingRetryJob() {
    var job = new BulkFormatEntity(analysisId);
    job.setStatus(BulkFormatStatus.WAITING_RETRY);
    when(bulkFormatRepository.findBulkFormatByAnalysisId(analysisId)).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.startBulkFormat(analysisId))
        .isInstanceOf(SmortException.class);
    verify(taskExecutor, never()).execute(any(Runnable.class));
  }
}
