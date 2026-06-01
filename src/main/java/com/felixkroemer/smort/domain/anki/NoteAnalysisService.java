package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.note.ChatMessageResponseMeta;
import com.felixkroemer.smort.domain.note.ChatMessageTextResponse;
import com.felixkroemer.smort.domain.note.ChatService;
import com.felixkroemer.smort.domain.note.StoreNoteToolResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteAnalysisService {

  private final AnalysisService analysisService;
  private final NoteRepository noteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final ChatService chatService;
  private final NoteTypeService noteTypeService;
  private final ChatRepository chatRepository;

  public Note getNote(UUID analysisId, Long noteId) {
    var note = noteRepository.findNoteById(analysisId, noteId);
    var noteTypes = noteTypeService.getNoteTypes(analysisId);
    var noteType = noteTypes.get(note.getNoteTypeId());
    var noteTypeFieldNames = noteType.getFields();
    var fields =
        IntStream.range(0, noteTypeFieldNames.size())
            .boxed()
            .collect(Collectors.toMap(noteTypeFieldNames::get, note.getFlds()::get));
    return new Note(note.getId(), note.getCards(), fields, note.getGuid());
  }

  public Optional<DerivedNoteEntity> getDerivedNote(UUID analysisId, Long noteId) {
    return derivedNoteRepository.findByNoteId(analysisId, noteId);
  }

  public Map<String, String> getContent(UUID analysisId, Long noteId) {
    return getDerivedNote(analysisId, noteId)
        .map(derivedNote -> Map.of("front", derivedNote.getFront(), "back", derivedNote.getBack()))
        .orElseGet(
            () -> {
              var note = this.getNote(analysisId, noteId);
              return note.getFlds();
            });
  }

  public DerivedNoteEntity formatNote(UUID analysisId, Long noteId) {
    var analysis = analysisService.getAnalysis(analysisId);

    var content = getContent(analysisId, noteId);
    var noteK = chatService.formatNote(content);

    var derivedNote =
        getDerivedNote(analysisId, noteId)
            .orElseGet(
                () -> new DerivedNoteEntity(analysisId, noteId, noteK.getFront(), noteK.getBack()));
    derivedNoteRepository.save(derivedNote);

    log.info("Formatted note. analysisId={}, noteId={}", analysisId, noteId);

    return derivedNote;
  }

  public List<ChatMessageResponseEntity> chat(UUID analysisId, Long noteId, String message) {
    var analysis = analysisService.getAnalysis(analysisId);

    var latestChatMessage =
        chatRepository.findLatestChatMessage(analysisId, noteId);
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var content = getContent(analysisId, noteId);
    var chatMessageResponse = chatService.chat(content, message, latestChatMessageResponseId);

    switch (chatMessageResponse) {
      case ChatMessageTextResponse r -> {
        return handleChatMessageTextResponse(
            analysisId, noteId, message, r, latestChatMessageResponseId);
      }
      case StoreNoteToolResponse r -> {
        return handleStoreNoteToolResponse(
            analysisId, noteId, message, r, latestChatMessageResponseId);
      }
    }
  }

  public List<ChatMessageResponseEntity> getChat(UUID analysisId, Long noteId) {
    return chatRepository.findAll(analysisId, noteId);
  }

  private @NonNull List<ChatMessageResponseEntity> handleStoreNoteToolResponse(
      UUID analysisId,
      Long noteId,
      String message,
      StoreNoteToolResponse r,
      Optional<String> latestChatMessageResponseId) {
    var toolCallChatMessageEntity =
        ChatMessageResponseEntity.toolCall(
            analysisId,
            noteId,
            message,
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.callId(),
            r.toolName());
    var derivedNote = new DerivedNoteEntity(analysisId, noteId, r.front(), r.back());
    derivedNoteRepository.save(derivedNote); // TODO: add to save tx
    var ackResponse = chatService.acknowledgeStoreNoteToolCall(r.callId(), r.meta().responseId());
    if (ackResponse instanceof ChatMessageTextResponse(String text, ChatMessageResponseMeta meta)) {
      var chatMessageEntity =
          ChatMessageResponseEntity.text(
              analysisId,
              noteId,
              Optional.empty(),
              meta.responseId(),
              latestChatMessageResponseId,
              text);
      chatRepository.saveInTransaction(toolCallChatMessageEntity, chatMessageEntity);
      return List.of(toolCallChatMessageEntity, chatMessageEntity);
    } else {
      throw new SmortException("Expected ChatMessageTextResponse in response to tool call ack.");
    }
  }

  private @NonNull List<ChatMessageResponseEntity> handleChatMessageTextResponse(
      UUID analysisId,
      Long noteId,
      String message,
      ChatMessageTextResponse r,
      Optional<String> latestChatMessageResponseId) {
    var chatMessageEntity =
        ChatMessageResponseEntity.text(
            analysisId,
            noteId,
            Optional.of(message),
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.text());
    chatRepository.save(chatMessageEntity);
    return List.of(chatMessageEntity);
  }
}
