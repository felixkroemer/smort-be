package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.StartAnalysisResponse;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnkiAnalysisService;
import com.felixkroemer.smort.infrastructure.sqlite.anki.EntityManagerFactoryCache;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("anki")
public class AnkiAnalysisController {

  private final AnkiAnalysisService ankiAnalysisService;
  private final EntityManagerFactoryCache cache;

  @PostMapping("/analysis")
  StartAnalysisResponse startAnalysis() {
    return new StartAnalysisResponse(ankiAnalysisService.createAnalysis());
  }

  @PostMapping("/upload")
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
}
