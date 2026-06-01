package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.ImportAnalysisRequest;
import com.felixkroemer.smort.domain.deck.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("deck")
public class DeckController {

  private final DeckService deckService;

  @PostMapping()
  public void importAnalysis(@RequestBody ImportAnalysisRequest importAnalysisRequest) {
    deckService.importDeck(importAnalysisRequest.id());
  }
}
