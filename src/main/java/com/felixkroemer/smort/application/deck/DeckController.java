package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.anki.ChatMessageMapper;
import com.felixkroemer.smort.application.anki.dto.ChatMessageRequest;
import com.felixkroemer.smort.application.anki.dto.ChatMessageResponse;
import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.application.deck.dto.ImportAnalysisRequest;
import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.deck.DeckService;
import com.felixkroemer.smort.domain.deck.NoteService;
import java.util.List;
import java.util.UUID;

import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("decks")
public class DeckController {

  private final DeckService deckService;
  private final NoteService noteService;
  private final ChatOrchestrationService chatOrchestrationService;

  private final DeckMapper deckMapper;
  private final NoteMapper noteMapper;
  private final ChatMessageMapper chatMessageMapper;

  @PostMapping()
  public void importAnalysis(@RequestBody ImportAnalysisRequest importAnalysisRequest) {
    deckService.importDeck(importAnalysisRequest.id());
  }

  @GetMapping
  public List<DeckResponse> getDecks() {
    var deckMetaEntities = deckService.getDecks();
    return deckMapper.toDto(deckMetaEntities);
  }

  @GetMapping("/{deckId}/notes/{noteId}")
  public NoteResponse getNote(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var note = noteService.getNote(deckId, noteId);
    return noteMapper.toDto(
        note.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/{deckId}/notes")
  public List<NoteResponse> getNotes(@PathVariable("deckId") UUID deckId) {
    var notes = noteService.getNotes(deckId);
    return noteMapper.toDto(notes);
  }

  @PatchMapping("/{deckId}/notes/{noteId}/format")
  public NoteResponse formatNote(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var note = noteService.formatNote(deckId, noteId);
    return noteMapper.toDto(note);
  }

  @PostMapping("/{deckId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> postChatMessage(
      @PathVariable("deckId") UUID deckId,
      @PathVariable("noteId") UUID noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses = noteService.chat(deckId, noteId, chatMessageRequest.message());
    return chatMessageMapper.toDto(chatMessageResponses);
  }

  @GetMapping("/{deckId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var chatMessageResponses = chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), noteId);
    return chatMessageMapper.toDto(chatMessageResponses);
  }
}
