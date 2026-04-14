package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.*;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.NoteAnalysisService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("anki/analysis")
public class AnalysisController {

  private final AnalysisService analysisService;
  private final NoteAnalysisService noteAnalysisService;
  private final NoteMapper noteMapper;

  @PostMapping()
  public StartAnalysisResponse startAnalysis() {
    return new StartAnalysisResponse(analysisService.createAnalysis());
  }

  @PostMapping("/db")
  public void uploadDb(
      @RequestParam("analysisId") UUID analysisId, @RequestParam("db") MultipartFile file) {
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new SmortException("Failed to get bytes of MultipartFile. analysisId={}", analysisId);
    }
    analysisService.uploadDB(analysisId, bytes);
  }

  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}")
  public NoteResponse getNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    var note = noteAnalysisService.getNote(analysisId, deckId, noteId);
    return noteMapper.toNoteResponseDto(note);
  }

  @GetMapping("/{analysisId}/decks")
  public List<DeckResponse> getDecks(@PathVariable("analysisId") UUID analysisId) {
    var decks = analysisService.getDecks(analysisId);
    return noteMapper.toDeckResponseDto(decks);
  }

  @GetMapping("/{analysisId}/notes/{deckId}")
  public List<NoteResponse> getNotes(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("deckId") Long deckId) {
    var notes = analysisService.getNotes(analysisId, deckId);
    return noteMapper.toNoteResponseDto(notes);
  }

  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}/derivedNote")
  public NoteResponse getDerivedNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    return noteAnalysisService
        .getDerivedNote(analysisId, deckId, noteId)
        .map(noteMapper::toNoteResponseDto)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  @GetMapping("/{analysisId}/notes/{deckId}/derivedNotes")
  public List<NoteResponse> getDerivedNotes(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("deckId") Long deckId) {
    return analysisService.getDerivedNotes(analysisId, deckId).stream()
        .map(noteMapper::toNoteResponseDto)
        .toList();
  }

  @PatchMapping("/{analysisId}/notes/{deckId}/{noteId}/format")
  public NoteResponse formatNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    var derivedNote = noteAnalysisService.formatNote(analysisId, deckId, noteId);
    return noteMapper.toNoteResponseDto(derivedNote);
  }

  @PostMapping("/{analysisId}/notes/{deckId}/{noteId}/chat")
  public String postChatMessage(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    return noteAnalysisService.chat(analysisId, deckId, noteId, chatMessageRequest.message());
  }

  /*  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    return null;
  }*/
}
