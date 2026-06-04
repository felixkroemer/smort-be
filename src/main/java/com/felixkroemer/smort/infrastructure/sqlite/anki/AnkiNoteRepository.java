package com.felixkroemer.smort.infrastructure.sqlite.anki;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnkiNoteRepository {

  private final EntityManagerFactoryCache entityManagerFactoryCache;

  public List<AnkiNoteEntity> findNotesByAnalysisIdAndDeckId(UUID analysisId, Long deckId) {
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

  public AnkiNoteEntity findNoteByAnalysisIdAndNoteId(UUID analysisId, Long noteId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT n FROM AnkiNoteEntity n WHERE n.id = :noteId", AnkiNoteEntity.class)
        .setParameter("noteId", noteId)
        .getSingleResult();
  }

  public List<AnkiNoteEntity> findNotesByAnalysisIdAndNoteIdIn(UUID analysisId, Set<Long> noteIds) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT n FROM AnkiNoteEntity n WHERE n.id IN :noteIds", AnkiNoteEntity.class)
        .setParameter("noteIds", noteIds)
        .getResultList();
  }

  public List<AnkiDeckEntity> findDecksByAnalysisId(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT d FROM AnkiDeckEntity d", AnkiDeckEntity.class)
        .getResultList();
  }

  public List<AnkiNoteTypeEntity> findNoteTypesByAnalysisId(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            """
                 SELECT nt FROM AnkiNoteTypeEntity nt
             """,
            AnkiNoteTypeEntity.class)
        .getResultList();
  }
}
