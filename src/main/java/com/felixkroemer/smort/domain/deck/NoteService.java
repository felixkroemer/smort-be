package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckRepository;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final DeckRepository deckRepository;
    
    public Optional<NoteEntity> getNote(UUID deckId, UUID noteId) {
        return deckRepository.findNoteByDeckIdAndNoteId(deckId, noteId);
    }

    public List<NoteEntity> getNotes(UUID deckId) {
        return deckRepository.findNotesByDeckId(deckId);
    }
    
}
