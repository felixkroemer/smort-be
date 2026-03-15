package com.felixkroemer.smort.infrastructure.sqlite.anki;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnkiNoteRepository {

  private final EntityManagerFactoryCache entityManagerFactoryCache;

  public List<AnkiNoteEntity> findNotesByDeckName(UUID analysisId, String deckName) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            """
                    SELECT n FROM AnkiNoteEntity n
                        JOIN n.cards c
                        JOIN c.deck d
                    WHERE d.name = :deckName
                    """,
            AnkiNoteEntity.class)
        .setParameter("deckName", deckName)
        .getResultList();
  }
}
