package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.*;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnkiAnalysisService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("anki/analysis")
public class AnkiAnalysisController {

  private final AnkiAnalysisService ankiAnalysisService;
  private final AnkiNoteMapper ankiNoteMapper;

  @PostMapping()
  public StartAnalysisResponse startAnalysis() {
    return new StartAnalysisResponse(ankiAnalysisService.createAnalysis());
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
    ankiAnalysisService.uploadDB(analysisId, bytes);
  }

  @GetMapping("/{analysisId}/notes")
  public List<AnkiNoteResponse> getNotes(
      @PathVariable("analysisId") UUID analysisId, @RequestParam("deckName") String deckName) {
    var notes = ankiAnalysisService.getNotes(analysisId, deckName);
    return ankiNoteMapper.toDto(notes);
  }

  // format the note, creating a new note (see /{analysisId}/notes/{noteId}/newNotes)
  @PatchMapping("/{analysisId}/notes/{noteId}/format")
  public DerivedNoteResponse formatNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return null;
  }

  @PostMapping("/{analysisId}/notes/{noteId}/chat")
  public ChatMessageResponse postChatMessage(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("noteId") Long noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    return null;
  }

  @GetMapping("/{analysisId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return null;
  }

  @GetMapping("/{analysisId}/notes/{noteId}/derivedNotes")
  public List<DerivedNoteResponse> getDerivedNotes(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return List.of();
  }
}
