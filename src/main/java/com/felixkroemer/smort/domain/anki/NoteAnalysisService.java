package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.note.ChatMessageTextResponse;
import com.felixkroemer.smort.domain.note.ChatService;
import com.felixkroemer.smort.domain.note.StoreNoteToolResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoteAnalysisService {

  private final NoteRepository noteRepository;
  private final DerivedNoteRepository derivedNoteRepository;
  private final ChatService chatService;
  private final ChatRepository chatRepository;

  public NoteEntity getNote(UUID analysisId, Long deckId, Long sourceNoteId) {
    return noteRepository.findById(analysisId, sourceNoteId);
  }

  public Optional<DerivedNoteEntity> getDerivedNote(
      UUID analysisId, Long deckId, Long sourceNoteId) {
    return derivedNoteRepository.findBySourceNoteId(analysisId, deckId, sourceNoteId);
  }

  public List<String> getContent(UUID analysisId, Long deckId, Long sourceNoteId) {
    return getDerivedNote(analysisId, deckId, sourceNoteId)
        .map(DerivedNoteEntity::getFlds)
        .orElseGet(
            () -> {
              var sourceNote = noteRepository.findById(analysisId, sourceNoteId);
              return sourceNote.getFlds();
            });
  }

  public DerivedNoteEntity formatNote(UUID analysisId, Long deckId, Long sourceNoteId) {
    var content = getContent(analysisId, deckId, sourceNoteId);
    var formattedContent = chatService.formatNote(content);

    var derivedNote =
        getDerivedNote(analysisId, deckId, sourceNoteId)
            .orElseGet(
                () -> new DerivedNoteEntity(analysisId, deckId, sourceNoteId, formattedContent));
    derivedNoteRepository.save(derivedNote);

    log.info(
        "Formatted note. analysisId={}, deckId={}, sourceNoteId={}",
        analysisId,
        deckId,
        sourceNoteId);

    return derivedNote;
  }

  public List<ChatMessageResponseEntity> chat(
      UUID analysisId, Long deckId, Long sourceNoteId, String message) {

    var latestChatMessage = chatRepository.findLatestChatMessage(analysisId, deckId, sourceNoteId);
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var content = getContent(analysisId, deckId, sourceNoteId);
    var chatMessageResponse = chatService.chat(content, message, latestChatMessageResponseId);

    switch (chatMessageResponse) {
      case ChatMessageTextResponse r -> {
        return handleChatMessageTextResponse(
            analysisId, deckId, sourceNoteId, message, r, latestChatMessageResponseId);
      }
      case StoreNoteToolResponse r -> {
        return handleStoreNoteToolResponse(
            analysisId, deckId, sourceNoteId, message, r, latestChatMessageResponseId);
      }
    }
  }

  private @NonNull List<ChatMessageResponseEntity> handleStoreNoteToolResponse(
      UUID analysisId,
      Long deckId,
      Long sourceNoteId,
      String message,
      StoreNoteToolResponse r,
      Optional<String> latestChatMessageResponseId) {
    var toolCallChatMessageEntity =
        ChatMessageResponseEntity.toolCall(
            analysisId,
            deckId,
            sourceNoteId,
            message,
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.callId(),
            r.toolName());
    var ackResponse = chatService.acknowledgeStoreNoteToolCall(r.callId(), r.meta().responseId());
    if (ackResponse
        instanceof
        ChatMessageTextResponse(
            String text,
            com.felixkroemer.smort.domain.note.ChatMessageResponseMeta meta)) {
      var chatMessageEntity =
          ChatMessageResponseEntity.text(
              analysisId,
              deckId,
              sourceNoteId,
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
      Long deckId,
      Long sourceNoteId,
      String message,
      ChatMessageTextResponse r,
      Optional<String> latestChatMessageResponseId) {
    var chatMessageEntity =
        ChatMessageResponseEntity.text(
            analysisId,
            deckId,
            sourceNoteId,
            Optional.of(message),
            r.meta().responseId(),
            latestChatMessageResponseId,
            r.text());
    chatRepository.save(chatMessageEntity);
    return List.of(chatMessageEntity);
  }
}
