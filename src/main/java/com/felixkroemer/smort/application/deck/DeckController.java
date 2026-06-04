package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.application.deck.dto.ImportAnalysisRequest;
import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.domain.deck.DeckService;
import com.felixkroemer.smort.domain.deck.NoteService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("deck")
public class DeckController {

  private final DeckService deckService;
  private final NoteService noteService;

  private final DeckMapper deckMapper;
  private final NoteMapper noteMapper;

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
}
