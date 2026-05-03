package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.*;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.NoteAnalysisService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
  private final NoteAnalysisService noteAnalysisService;
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

  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}")
  public SourceNoteResponse getNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    var note = noteAnalysisService.getNote(analysisId, deckId, noteId);
    return noteMapper.toSourceNoteResponseDto(note);
  }

  @GetMapping("/{analysisId}/decks")
  public List<DeckResponse> getDecks(@PathVariable("analysisId") UUID analysisId) {
    var decks = analysisService.getDecks(analysisId);
    return noteMapper.toDeckResponseDto(decks);
  }

  @GetMapping("/{analysisId}/notes/{deckId}")
  public List<SourceNoteResponse> getNotes(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("deckId") Long deckId) {
    var notes = analysisService.getNotes(analysisId, deckId);
    return noteMapper.toSourceNoteResponseDto(notes);
  }

  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}/derivedNote")
  public DerivedNoteResponse getDerivedNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    return noteMapper.toDerivedNoteResponseDto(
        noteAnalysisService
            .getDerivedNote(analysisId, deckId, noteId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  @GetMapping("/{analysisId}/notes/{deckId}/derivedNotes")
  public List<DerivedNoteResponse> getDerivedNotes(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("deckId") Long deckId) {
    return analysisService.getDerivedNotes(analysisId, deckId).stream()
        .map(noteMapper::toDerivedNoteResponseDto)
        .toList();
  }

  @GetMapping("/{analysisId}/notes/derivedNotes/export")
  public ResponseEntity<byte[]> createDerivedNotesExport(
      @PathVariable("analysisId") UUID analysisId) {

    var derivedNotes = analysisService.getAllDerivedNotes(analysisId);

    StringBuilder sb = new StringBuilder();
    sb.append("#separator:tab\n");
    sb.append("#html:false\n");
    sb.append("#guid column:1\n");

    for (var derivedNoteExportEntry : derivedNotes) {
      sb.append(derivedNoteExportEntry.guid());
      sb.append("\t");
      sb.append(
          String.join(
              "\t",
              derivedNoteExportEntry.derivedNote().getFlds().stream()
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

  @PatchMapping("/{analysisId}/notes/{deckId}/{noteId}/format")
  public DerivedNoteResponse formatNote(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    var derivedNote = noteAnalysisService.formatNote(analysisId, deckId, noteId);
    return noteMapper.toDerivedNoteResponseDto(derivedNote);
  }

  @PostMapping("/{analysisId}/notes/{deckId}/{noteId}/chat")
  public List<ChatMessageResponseDTO> postChatMessage(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses =
        noteAnalysisService.chat(analysisId, deckId, noteId, chatMessageRequest.message());
    return chatMessageMapper.toDto(chatMessageResponses);
  }

  @GetMapping("/{analysisId}/notes/{deckId}/{noteId}/chat")
  public List<ChatMessageResponseDTO> getChat(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("deckId") Long deckId,
      @PathVariable("noteId") Long noteId) {
    return chatMessageMapper.toDto(noteAnalysisService.getChat(analysisId, deckId, noteId));
  }
}
