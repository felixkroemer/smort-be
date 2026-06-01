package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeckService {

  private final AnalysisService analysisService;
  private final DeckRepository deckRepository;

  public void importDeck(UUID analysisId) {
    var notes = analysisService.getNotes(analysisId);
    var derivedNotes = analysisService.getDerivedNotes(analysisId);
    var derivedNoteKeys =
        derivedNotes.stream().map(DerivedNoteEntity::getNoteId).collect(Collectors.toSet());

    var unmapped =
        notes.stream()
            .filter(n -> !derivedNoteKeys.contains(n.getId()))
            .collect(Collectors.toSet());

    var deckId = UUID.randomUUID();

    derivedNotes.stream()
        .map(d -> new NoteEntity(deckId, UUID.randomUUID(), d.getFront(), d.getBack()))
        .forEach(deckRepository::save);

    unmapped.stream()
        .map(u -> u.getFlds().entrySet().stream().map(e -> e.getKey() + "\n" + e.getValue()))
        .map(x -> String.join("\n", x.toList()))
        .map(text -> new NoteEntity(deckId, UUID.randomUUID(), "Front", text))
        .forEach(deckRepository::save);
  }
}
