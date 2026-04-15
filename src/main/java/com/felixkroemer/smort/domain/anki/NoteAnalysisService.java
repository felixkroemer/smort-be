package com.felixkroemer.smort.domain.anki;

import com.felixkroemer.smort.domain.note.ChatMessageTextResponse;
import com.felixkroemer.smort.domain.note.ChatService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.*;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.AbstractChatMessageEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  public ChatMessageEntity chat(UUID analysisId, Long deckId, Long sourceNoteId, String message) {

    var latestChatMessage = chatRepository.findLatestChatMessage(analysisId, deckId, sourceNoteId);
    var latestChatMessageResponseId =
        latestChatMessage.map(AbstractChatMessageEntity::getResponseId);

    var content = getContent(analysisId, deckId, sourceNoteId);
    var response = chatService.chat(content, message, latestChatMessageResponseId);

    switch (response) {
      case ChatMessageTextResponse chatMessageTextResponse -> {
        var chatMessageEntity =
            new ChatMessageEntity(
                analysisId,
                deckId,
                sourceNoteId,
                chatMessageTextResponse.text(),
                message,
                chatMessageTextResponse.meta().responseId(),
                latestChatMessageResponseId,
                Instant.now());
        chatRepository.save(chatMessageEntity);
        return chatMessageEntity;
      }
    }
  }
}
