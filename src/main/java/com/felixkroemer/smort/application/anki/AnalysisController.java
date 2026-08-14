package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.*;
import com.felixkroemer.smort.application.anki.mapping.AnalysisRestMapper;
import com.felixkroemer.smort.application.anki.mapping.AnkiNoteRestMapper;
import com.felixkroemer.smort.application.anki.mapping.BulkFormatRestMapper;
import com.felixkroemer.smort.application.chat.dto.ChatMessageRequest;
import com.felixkroemer.smort.application.chat.dto.ChatMessageResponse;
import com.felixkroemer.smort.application.chat.mapping.ChatMessageRestMapper;
import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.domain.anki.AnkiNoteService;
import com.felixkroemer.smort.domain.anki.AnkiNoteTypeService;
import com.felixkroemer.smort.domain.anki.BulkFormatService;
import com.felixkroemer.smort.domain.chat.ChatOrchestrationService;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteTypeEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("analysis")
public class AnalysisController {

  private final AnalysisService analysisService;
  private final AnkiNoteService ankiNoteService;
  private final ChatOrchestrationService chatOrchestrationService;
  private final AnkiNoteTypeService ankiNoteTypeService;
  private final BulkFormatService bulkFormatService;

  private final AnalysisRestMapper analysisRestMapper;
  private final AnkiNoteRestMapper ankiNoteRestMapper;
  private final BulkFormatRestMapper bulkFormatRestMapper;
  private final ChatMessageRestMapper chatMessageRestMapper;

  @PostMapping()
  public StartAnalysisResponse startAnalysis() {
    return new StartAnalysisResponse(analysisService.createAnalysis());
  }

  @PostMapping("/{analysisId}/uploadDb")
  public void uploadDb(
      @PathVariable("analysisId") UUID analysisId, @RequestParam("db") MultipartFile file) {
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new SmortException("Failed to get bytes of MultipartFile. analysisId={}", analysisId);
    }
    analysisService.uploadDB(analysisId, bytes);
  }

  @GetMapping("/{analysisId}/decks")
  public List<AnkiDeckResponse> getDecks(@PathVariable("analysisId") UUID analysisId) {
    var decks = analysisService.getDecks(analysisId);
    return ankiNoteRestMapper.toAnkiDeckResponse(decks);
  }

  @PostMapping("/{analysisId}/setDeck")
  public void setDeck(
      @PathVariable("analysisId") UUID analysisId, @RequestParam("deckId") Long deckId) {
    analysisService.setDeck(analysisId, deckId);
  }

  @GetMapping("/{analysisId}")
  public AnalysisResponse getAnalysis(@PathVariable("analysisId") UUID analysisId) {
    return analysisRestMapper.toAnalysisResponse(analysisService.getAnalysis(analysisId));
  }

  @GetMapping("/{analysisId}/settings")
  public AnalysisSettingsResponse getAnalysisSettings(
      @PathVariable("analysisId") UUID analysisId) {
    return analysisRestMapper.toAnalysisSettingsResponse(
        analysisService.getAnalysisSettings(analysisId));
  }

  @PatchMapping("/{analysisId}/settings")
  public AnalysisSettingsResponse updateAnalysisSettings(
      @PathVariable("analysisId") UUID analysisId,
      @RequestBody UpdateAnalysisSettingsRequest updateAnalysisSettingsRequest) {
    return analysisRestMapper.toAnalysisSettingsResponse(
        analysisService.updateAnalysisSettings(
            analysisId, updateAnalysisSettingsRequest.formatInstructions()));
  }

  @DeleteMapping("/{analysisId}")
  public void deleteAnalysis(@PathVariable("analysisId") UUID analysisId) {
    analysisService.deleteAnalysis(analysisId);
  }

  @GetMapping
  public List<AnalysisResponse> getAnalyses() {
    return analysisRestMapper.toAnalysisResponse(analysisService.getAnalyses());
  }

  @GetMapping("/{analysisId}/notes/{noteId}")
  public AnkiNoteResponse getNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    var note = ankiNoteService.getNote(analysisId, noteId);
    return ankiNoteRestMapper.toAnkiNoteResponse(note);
  }

  @GetMapping("/{analysisId}/notes")
  public List<AnkiNoteResponse> getNotes(@PathVariable("analysisId") UUID analysisId) {
    var notes = analysisService.getNotes(analysisId);
    return ankiNoteRestMapper.toAnkiNoteResponse(notes);
  }

  @GetMapping("/{analysisId}/notes/{noteId}/derivedNote")
  public DerivedNoteResponse getDerivedNote(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    return ankiNoteRestMapper.toDerivedNoteResponse(
        ankiNoteService
            .getDerivedNote(analysisId, noteId)
            .orElseThrow(
                () -> new NotFoundException("Could not find derived note. id={}", noteId)));
  }

  @GetMapping("/{analysisId}/derivedNotes")
  public List<DerivedNoteResponse> getDerivedNotes(@PathVariable("analysisId") UUID analysisId) {
    return analysisService.getDerivedNotes(analysisId).stream()
        .map(ankiNoteRestMapper::toDerivedNoteResponse)
        .toList();
  }

  @GetMapping("/{analysisId}/noteTypes")
  public Map<String, List<String>> getNoteTypes(@PathVariable("analysisId") UUID analysisId) {
    var noteTypes = analysisService.getNoteTypes(analysisId);
    return noteTypes.stream()
        .collect(Collectors.toMap(AnkiNoteTypeEntity::getName, AnkiNoteTypeEntity::getFields));
  }

  @GetMapping("/{analysisId}/derivedNotes/export")
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
    var derivedNote = ankiNoteService.formatNote(analysisId, noteId);
    return ankiNoteRestMapper.toDerivedNoteResponse(derivedNote);
  }

  @PostMapping("/{analysisId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> postChatMessage(
      @PathVariable("analysisId") UUID analysisId,
      @PathVariable("noteId") Long noteId,
      @RequestBody ChatMessageRequest chatMessageRequest) {
    var chatMessageResponses =
        ankiNoteService.chat(analysisId, noteId, chatMessageRequest.message());
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @GetMapping("/{analysisId}/notes/{noteId}/chat")
  public List<ChatMessageResponse> getChat(
      @PathVariable("analysisId") UUID analysisId, @PathVariable("noteId") Long noteId) {
    var chatMessageResponses =
        chatOrchestrationService.getChat(AnalysisKeys.analysisPk(analysisId), noteId);
    return chatMessageRestMapper.toChatMessageResponse(chatMessageResponses);
  }

  @PostMapping("/{analysisId}/format")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void startBulkFormat(
      @PathVariable UUID analysisId,
      @RequestParam(defaultValue = "true") boolean reformatAlreadyFormatted) {
    bulkFormatService.startBulkFormat(analysisId, reformatAlreadyFormatted);
  }

  @GetMapping("/{analysisId}/format/status")
  public BulkFormatResponse getBulkFormatStatus(@PathVariable UUID analysisId) {
    return bulkFormatRestMapper.toBulkFormatResponse(bulkFormatService.getJobStatus(analysisId));
  }
}
