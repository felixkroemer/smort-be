package com.felixkroemer.smort.infrastructure.sqlite.anki;

import java.util.List;
import java.util.Set;
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

  public NoteEntity findNoteById(UUID analysisId, Long noteId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT n FROM NoteEntity n WHERE n.id = :noteId", NoteEntity.class)
        .setParameter("noteId", noteId)
        .getSingleResult();
  }

  public List<NoteEntity> findNotesByIdIn(UUID analysisId, Set<Long> ids) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT n FROM NoteEntity n WHERE n.id IN :ids", NoteEntity.class)
        .setParameter("ids", ids)
        .getResultList();
  }

  public List<DeckEntity> findAllDecks(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery("SELECT d FROM DeckEntity d", DeckEntity.class)
        .getResultList();
  }

  public List<NoteTypeEntity> getNoteTypes(UUID analysisId) {
    var entityManager = entityManagerFactoryCache.getOrCreate(analysisId);
    return entityManager
        .createQuery(
            """
                 SELECT nt FROM NoteTypeEntity nt
             """,
            NoteTypeEntity.class)
        .getResultList();
  }
}
