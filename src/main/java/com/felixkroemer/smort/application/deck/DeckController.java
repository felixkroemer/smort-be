package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.anki.dto.BulkFormatResponse;
import com.felixkroemer.smort.application.anki.mapping.BulkFormatRestMapper;
import com.felixkroemer.smort.application.chat.dto.ChatMessageRequest;
import com.felixkroemer.smort.application.chat.dto.ChatMessageResponse;
import com.felixkroemer.smort.application.chat.mapping.ChatMessageRestMapper;
import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.application.deck.dto.DraftNoteResponse;
import com.felixkroemer.smort.application.deck.dto.ImportAnalysisRequest;
import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.application.deck.mapping.DeckRestMapper;
import com.felixkroemer.smort.application.deck.mapping.DraftNoteRestMapper;
import com.felixkroemer.smort.application.deck.mapping.NoteRestMapper;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.domain.deck.DeckBulkFormatService;
import com.felixkroemer.smort.domain.deck.DeckService;
import com.felixkroemer.smort.domain.deck.NoteService;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("decks")
public class DeckController {

  private final DeckService deckService;
  private final NoteService noteService;
  private final ChatOrchestrationService chatOrchestrationService;
  private final DeckBulkFormatService deckBulkFormatService;

  private final DeckRestMapper deckRestMapper;
  private final BulkFormatRestMapper bulkFormatRestMapper;
  private final NoteRestMapper noteRestMapper;
  private final ChatMessageRestMapper chatMessageRestMapper;
  private final DraftNoteRestMapper draftNoteRestMapper;

  @PostMapping()
  public DeckResponse importAnalysis(@RequestBody ImportAnalysisRequest importAnalysisRequest) {
    return deckRestMapper.toDeckResponse(
        deckService.importDeck(importAnalysisRequest.id(), importAnalysisRequest.templates()));
  }

  @GetMapping
  public List<DeckResponse> getDecks() {
    var deckMetaEntities = deckService.getDecks();
    return deckRestMapper.toDeckResponse(deckMetaEntities);
  }

  @GetMapping("/{deckId}/notes/{noteId}")
  public NoteResponse getNote(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var note = noteService.getNote(deckId, noteId);
    return noteRestMapper.toNoteResponse(
        note.orElseThrow(() -> new NotFoundException("Could not find note. id={}", noteId)));
  }

  @GetMapping("/{deckId}/notes")
  public List<NoteResponse> getNotes(@PathVariable("deckId") UUID deckId) {
    var notes = deckService.getNotes(deckId);
    return noteRestMapper.toNoteResponse(notes);
  }

  @PatchMapping("/{deckId}/notes/{noteId}/format")
  public List<ChatMessageResponse> formatNote(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var chatMessages = noteService.formatNote(deckId, noteId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessages);
  }

  @PostMapping("/{deckId}/format")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void startBulkFormat(
      @PathVariable UUID deckId,
      @RequestParam(defaultValue = "true") boolean reformatAlreadyFormatted) {
    deckBulkFormatService.startBulkFormat(deckId, reformatAlreadyFormatted);
  }

  @GetMapping("/{deckId}/format/status")
  public BulkFormatResponse getBulkFormatStatus(@PathVariable UUID deckId) {
    return bulkFormatRestMapper.toBulkFormatResponse(deckBulkFormatService.getJobStatus(deckId));
  }

  @PostMapping("/{deckId}/format/cancel")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void cancelBulkFormat(@PathVariable UUID deckId) {
    deckBulkFormatService.cancelBulkFormat(deckId);
  }

  @PostMapping("/{deckId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> postChatMessage(
      @PathVariable("deckId") UUID deckId,
      @PathVariable("noteId") UUID noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses = noteService.chat(deckId, noteId, chatMessageRequest.message());
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @GetMapping("/{deckId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    var chatMessageResponses = chatOrchestrationService.getChat(DeckKeys.deckPk(deckId), noteId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @GetMapping("/{deckId}/draft-note")
  public DraftNoteResponse getDraftNote(@PathVariable("deckId") UUID deckId) {
    return draftNoteRestMapper.toDraftNoteResponse(deckService.getDraftNote(deckId));
  }

  @PostMapping("/{deckId}/chat")
  public List<ChatMessageResponse> postDeckChatMessage(
      @PathVariable("deckId") UUID deckId, @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses = deckService.chat(deckId, chatMessageRequest.message());
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @GetMapping("/{deckId}/chat")
  public List<ChatMessageResponse> getDeckChat(@PathVariable("deckId") UUID deckId) {
    var chatMessageResponses = deckService.getChat(deckId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @DeleteMapping("/{deckId}")
  public void deleteDeck(@PathVariable("deckId") UUID deckId) {
    deckService.deleteDeck(deckId);
  }

  @DeleteMapping("/{deckId}/notes/{noteId}")
  public void deleteDeck(@PathVariable("deckId") UUID deckId, @PathVariable("noteId") UUID noteId) {
    deckService.deleteNote(deckId, noteId);
  }
}
