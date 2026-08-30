package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.anki.mapping.DerivedNoteEntityMapper;
import com.felixkroemer.smort.domain.chat.*;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

  public Map<String, String> getContent(UUID analysisId, Long noteId) {
    var note = getNote(analysisId, noteId);
    return getDerivedNote(analysisId, noteId)
        .map(DerivedNoteEntity::getContent)
        .orElseGet(note::getContent);
  }

  public List<ChatMessageEntity> formatNote(UUID analysisId, Long noteId) {
    var analysis = analysisService.getAnalysis(analysisId);
    var content = getContent(analysisId, noteId);

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatMessage) toolCall;
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
            analysis.getFormatInstructions(),
            toolHandlers);

    log.info("Formatted note. analysisId={}, noteId={}", analysisId, noteId);

    return chatMessages;
  }

  public List<ChatMessageEntity> chat(UUID analysisId, Long noteId, String message) {
    var content = getContent(analysisId, noteId);

    var formatInstructions = analysisService.getAnalysisSettings(analysisId).formatInstructions();

    var ctx = new NoteChatContext<>(noteId, content);

    Map<Class<? extends ChatMessage>, ToolCallHandler> toolHandlers =
        Map.of(
            StoreNoteToolChatMessage.class,
            (tx, toolCall) -> {
              var m = (StoreNoteToolChatMessage) toolCall;
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
