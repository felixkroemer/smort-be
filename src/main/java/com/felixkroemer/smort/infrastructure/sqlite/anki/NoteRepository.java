package com.felixkroemer.smort.infrastructure.sqlite.anki;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteRepository {

  private final EntityManagerFactoryCache entityManagerFactoryCache;

  public List<NoteEntity> findNotesByDeck(UUID analysisId, Long deckId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            """
                    SELECT n FROM NoteEntity n
                        JOIN n.cards c
                        JOIN c.deck d
                    WHERE d.id = :deckId
                    """,
            NoteEntity.class)
        .setParameter("deckId", deckId)
        .getResultList();
  }

  public NoteEntity findById(UUID analysisId, Long sourceNoteId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            "SELECT n FROM NoteEntity n WHERE n.id = :sourceNoteId", NoteEntity.class)
        .setParameter("sourceNoteId", sourceNoteId)
        .getSingleResult();
  }

  public List<DeckEntity> findAllDecks(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT d FROM DeckEntity d", DeckEntity.class)
        .getResultList();
  }
}
