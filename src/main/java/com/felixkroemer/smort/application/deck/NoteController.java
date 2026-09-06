package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.application.deck.mapping.NoteRestMapper;
import com.felixkroemer.smort.domain.deck.DeckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("notes")
public class NoteController {

  private final DeckService deckService;
  private final NoteRestMapper noteRestMapper;

  @GetMapping
  public List<NoteResponse> getAllNotes() {
    return noteRestMapper.toNoteResponse(deckService.getAllNotes());
  }
}
