package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.*;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.domain.user.FormattingSettingsResolver;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnkiNoteService {

  private final AnkiNoteRepository ankiNoteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final ChatOrchestrationService chatOrchestrationService;
  private final ChatRepository chatRepository;
  private final AnkiNoteTypeService noteTypeService;
  private final AnalysisService analysisService;
  private final DerivedNoteEntityMapper derivedNoteEntityMapper;
  private final FormattingSettingsResolver formattingSettingsResolver;

  public AnkiNote getNote(UUID analysisId, Long noteId) {
    var note = ankiNoteRepository.findNoteByAnalysisIdAndNoteId(analysisId, noteId);
    return new AnkiNote(
        note.getId(),
        noteTypeService.getContent(analysisId, note),
        note.getGuid(),
        note.getNoteTypeId());
  }

  public Optional<DerivedNoteEntity> getDerivedNote(UUID analysisId, Long noteId) {
    return derivedNoteRepository.findDerivedNotedByAnalysisIdAndNoteId(analysisId, noteId);
  }

  public List<AnkiNote> getNotes(UUID analysisId) {
    var analysis = analysisService.getAnalysis(analysisId);
    var notes = ankiNoteRepository.findNotesByAnalysisIdAndDeckId(analysisId, analysis.getDeckId());
    return notes.stream()
        .map(
            n ->
                new AnkiNote(
                    n.getId(),
                    noteTypeService.getContent(analysisId, n),
                    n.getGuid(),
                    n.getNoteTypeId()))
        .toList();
  }

  public List<DerivedNoteEntity> getDerivedNotes(UUID analysisId) {
    return derivedNoteRepository.findDerivedNotesByAnalysisId(analysisId);
  }

  public Map<DerivedNoteEntity, String> getDerivedNoteToGuidMapping(
      UUID analysisId, List<DerivedNoteEntity> derivedNotes) {
    var derivedNoteIds =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());
    var guidByNoteId =
        ankiNoteRepository.findNotesByAnalysisIdAndNoteIdIn(analysisId, derivedNoteIds).stream()
            .collect(Collectors.toMap(AnkiNoteEntity::getId, AnkiNoteEntity::getGuid));

    return derivedNotes.stream()
        .collect(Collectors.toMap(Function.identity(), d -> guidByNoteId.get(d.getNoteId())));
  }

  public Map<String, String> getContent(UUID analysisId, Long noteId) {
    var note = getNote(analysisId, noteId);
    return getDerivedNote(analysisId, noteId)
        .map(DerivedNoteEntity::getContent)
        .orElseGet(note::getContent);
  }

  public List<ChatMessageEntity> formatNote(UUID analysisId, Long noteId) {
    var content = getContent(analysisId, noteId);
    var formatInstructions =
        formattingSettingsResolver.resolve(analysisService.getAnalysis(analysisId).getAnalysisSettings());

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatResponse) toolCall;
              var derivedNote =
                  getDerivedNote(analysisId, noteId)
                      .map(
                          d -> {
                            d.setFront(m.front());
                            d.setBack(m.back());
                            d.setLastFormattedAt(Optional.of(Instant.now()));
                            return d;
                          })
                      .orElseGet(
                          () ->
                              derivedNoteEntityMapper.toDerivedNoteEntity(
                                  analysisId, noteId, new NoteSchema(m.front(), m.back())));
              derivedNoteRepository.saveInTx(tx, derivedNote);
            });

    var chatMessages =
        chatOrchestrationService.formatNote(
            AnalysisKeys.analysisPk(analysisId),
            noteId,
            content,
            formatInstructions,
            toolHandlers);

    log.info("Formatted note. analysisId={}, noteId={}", analysisId, noteId);

    return chatMessages;
  }

  public DerivedNoteEntity updateNote(UUID analysisId, Long noteId, String front, String back) {
    if (!ankiNoteRepository.noteExists(analysisId, noteId)) {
      throw new NotFoundException("Note not found. id={}", noteId);
    }

    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .map(
                d -> {
                  d.setFront(front);
                  d.setBack(back);
                  return d;
                })
            .orElseGet(() -> new DerivedNoteEntity(analysisId, noteId, front, back));

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> derivedNoteRepository.saveInTx(tx, derivedNote));

    chatOrchestrationService.storeNote(
        AnalysisKeys.analysisPk(analysisId), noteId, front, back, toolHandlers);

    return derivedNote;
  }

  public List<ChatMessageEntity> chat(UUID analysisId, Long noteId, String message) {
    var content = getContent(analysisId, noteId);

    var formatInstructions =
        formattingSettingsResolver.resolve(analysisService.getAnalysis(analysisId).getAnalysisSettings());

    var ctx = new NoteChatContext<>(noteId, content);

    Map<Class<? extends ChatResponse>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatResponse.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatResponse) toolCall;
              derivedNoteRepository.saveInTx(
                  tx,
                  derivedNoteEntityMapper.toDerivedNoteEntity(
                      analysisId, noteId, new NoteSchema(m.front(), m.back())));
            });

    return chatOrchestrationService.noteChat(
        AnalysisKeys.analysisPk(analysisId), ctx, message, formatInstructions, toolHandlers);
  }

  public void clearChat(UUID analysisId, Long noteId) {
    chatRepository.deleteChat(AnalysisKeys.analysisPk(analysisId), noteId);
  }
}
