package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.anki.AnalysisService;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.util.List;
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
    var analysis = analysisService.getAnalysis(analysisId);

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
        .forEach(deckRepository::saveNote);

    unmapped.stream()
        .map(u -> u.getFlds().entrySet().stream().map(e -> e.getKey() + "\n" + e.getValue()))
        .map(x -> String.join("\n", x.toList()))
        .map(text -> new NoteEntity(deckId, UUID.randomUUID(), "Front", text))
        .forEach(deckRepository::saveNote);

    deckRepository.saveDeckMeta(new DeckMetaEntity(deckId, analysis.getDeckName(), "default"));
  }

  public List<DeckMetaEntity> getDecks() {
    return deckRepository.findDeckMetasByUserId("default");
  }

  public void deleteDeck(UUID deckId) {
    var deck =
        deckRepository
            .findDeckMetaByDeckId(deckId)
            .orElseThrow(() -> new SmortException("Could not find deck. deckId={}", deckId));
    if (deck.getStatus() == DeckStatus.MARKED_FOR_DELETION) {
      throw new SmortException("Deck is already marked for deletion. deckId={}", deckId);
    }
    deck.setStatus(DeckStatus.MARKED_FOR_DELETION);
    deckRepository.saveDeckMeta(deck);
  }

  public void deleteNote(UUID deckId, UUID noteId) {
    deckRepository.deleteNoteByDeckIdAndNoteId(deckId, noteId);
  }
}
