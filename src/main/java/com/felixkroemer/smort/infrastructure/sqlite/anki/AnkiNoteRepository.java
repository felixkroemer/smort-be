package com.felixkroemer.smort.infrastructure.sqlite.anki;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnkiNoteRepository {

  private final EntityManagerFactoryCache entityManagerFactoryCache;

  public List<AnkiNoteEntity> findNotesByDeck(UUID analysisId, Long deckId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            """
                    SELECT n FROM AnkiNoteEntity n
                        JOIN n.cards c
                        JOIN c.deck d
                    WHERE d.id = :deckId
                    """,
            AnkiNoteEntity.class)
        .setParameter("deckId", deckId)
        .getResultList();
  }

  public AnkiNoteEntity findById(UUID analysisId, Long sourceNoteId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            "SELECT n FROM AnkiNoteEntity n WHERE n.id = :sourceNoteId", AnkiNoteEntity.class)
        .setParameter("sourceNoteId", sourceNoteId)
        .getSingleResult();
  }

  public List<AnkiDeckEntity> findAllDecks(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT d FROM AnkiDeckEntity d", AnkiDeckEntity.class)
        .getResultList();
  }
}
