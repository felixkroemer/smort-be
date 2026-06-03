package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.application.deck.dto.ImportAnalysisRequest;
import com.felixkroemer.smort.domain.deck.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("deck")
public class DeckController {

  private final DeckService deckService;
  private final DeckMapper deckMapper;

  @PostMapping()
  public void importAnalysis(@RequestBody ImportAnalysisRequest importAnalysisRequest) {
    deckService.importDeck(importAnalysisRequest.id());
  }
  
  @GetMapping
  public List<DeckResponse> getDecks() {
    var deckMetaEntities = deckService.getDecks();
    return deckMapper.toDto(deckMetaEntities);
  }
}
