package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.*;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNoteAnalysisService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("anki/analysis")
public class AnalysisController {

  private final AnalysisService analysisService;
  private final AnalysisMapper analysisMapper;
  private final AnkiNoteAnalysisService ankiNoteAnalysisService;
  private final NoteMapper noteMapper;
  private final ChatMessageMapper chatMessageMapper;

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

  @GetMapping("/{analysisId}")
  public AnalysisResponse getAnalysis(@PathVariable("analysisId") UUID analysisId) {
    return analysisMapper.toAnalysisResponse(analysisService.getAnalysis(analysisId));
  }

  @GetMapping
  public List<AnalysisResponse> getAnalyses() {
    return analysisMapper.toAnalysisResponse(analysisService.getAnalyses());
  }

  @PostMapping("/deck")
  public void setDeck(
      @RequestParam("analysisId") UUID analysisId, @RequestParam("deckId") Long deckId) {
    analysisService.setDeck(analysisId, deckId);
  }

  @GetMapping("/{analysisId}/notes/{noteId}")
  public NoteResponse getNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    var note = ankiNoteAnalysisService.getNote(analysisId, noteId);
    return noteMapper.toNoteResponseDto(note);
  }

  @GetMapping("/{analysisId}/decks")
  public List<DeckResponse> getDecks(@PathVariable("analysisId") UUID analysisId) {
    var decks = analysisService.getDecks(analysisId);
    return noteMapper.toDeckResponseDto(decks);
  }

  @GetMapping("/{analysisId}/notes")
  public List<NoteResponse> getNotes(@PathVariable("analysisId") UUID analysisId) {
    var notes = analysisService.getNotes(analysisId);
    return noteMapper.toNoteResponseDto(notes);
  }

  @GetMapping("/{analysisId}/notes/{noteId}/derivedNote")
  public DerivedNoteResponse getDerivedNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return noteMapper.toDerivedNoteResponseDto(
        ankiNoteAnalysisService
            .getDerivedNote(analysisId, noteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/{analysisId}/notes/derivedNotes")
  public List<DerivedNoteResponse> getDerivedNotes(@PathVariable("analysisId") UUID analysisId) {
    return analysisService.getDerivedNotes(analysisId).stream()
        .map(noteMapper::toDerivedNoteResponseDto)
        .toList();
  }

  @GetMapping("/{analysisId}/notes/derivedNotes/export")
  public ResponseEntity<byte[]> createDerivedNotesExport(
      @PathVariable("analysisId") UUID analysisId) {

    var derivedNotes = analysisService.getDerivedNotes(analysisId);
    var derivedNotesGuidMapping =
        analysisService.getDerivedNoteToGuidMapping(analysisId, derivedNotes);

    StringBuilder sb = new StringBuilder();
    sb.append("#separator:tab\n");
    sb.append("#html:false\n");
    sb.append("#guid column:1\n");

    for (var derivedNote : derivedNotes) {
      sb.append(derivedNotesGuidMapping.get(derivedNote));
      sb.append("\t");
      sb.append(
          String.join(
              "\t",
              Stream.of(derivedNote.getBack())
                  .map(fld -> fld.replace("\"", "\"\""))
                  .map(fld -> "\"" + fld + "\"")
                  .toList()));
      sb.append("\n");
    }

    var content = sb.toString().getBytes(StandardCharsets.UTF_8);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv"));
    headers.setContentDispositionFormData("attachment", "export.csv");
    headers.setContentLength(content.length);

    return new ResponseEntity<>(content, headers, HttpStatus.OK);
  }

  @PatchMapping("/{analysisId}/notes/{noteId}/format")
  public DerivedNoteResponse formatNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    var derivedNote = ankiNoteAnalysisService.formatNote(analysisId, noteId);
    return noteMapper.toDerivedNoteResponseDto(derivedNote);
  }

  @PostMapping("/{analysisId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> postChatMessage(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("noteId") Long noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses =
        ankiNoteAnalysisService.chat(analysisId, noteId, chatMessageRequest.message());
    return chatMessageMapper.toDto(chatMessageResponses);
  }

  @GetMapping("/{analysisId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return chatMessageMapper.toDto(ankiNoteAnalysisService.getChat(analysisId, noteId));
  }
}
