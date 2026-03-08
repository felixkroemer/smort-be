package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.AnkiNoteResponse;
import com.felixkroemer.smort.application.anki.dto.StartAnalysisResponse;
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
  StartAnalysisResponse startAnalysis() {
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

  @GetMapping("/notes")
  public List<AnkiNoteResponse> getNotes(
      @RequestParam("analysisId") UUID analysisId, @RequestParam("deckName") String deckName) {
    var notes = ankiAnalysisService.getNotes(analysisId, deckName);
    return ankiNoteMapper.toDto(notes);
  }
}
