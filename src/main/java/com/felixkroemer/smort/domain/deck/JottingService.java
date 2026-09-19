package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.common.exception.NotFoundException;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JottingService {

  private final JottingRepository jottingRepository;

  public JottingEntity create(UUID deckId, String title, String description) {
    var jotting = new JottingEntity(deckId, UUID.randomUUID(), title, description);
    jottingRepository.save(jotting);
    return jotting;
  }

  public List<JottingEntity> getJottings(UUID deckId) {
    return jottingRepository.findJottingsByDeckId(deckId);
  }

  public JottingEntity getJotting(UUID deckId, UUID jottingId) {
    return jottingRepository
        .findJotting(deckId, jottingId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Could not find jotting. deckId={}, jottingId={}", deckId, jottingId));
  }

  public JottingEntity update(UUID deckId, UUID jottingId, String title, String description) {
    var jotting = getJotting(deckId, jottingId);
    if (title != null) jotting.setTitle(title);
    if (description != null) jotting.setDescription(description);
    jottingRepository.save(jotting);
    return jotting;
  }

  public void delete(UUID deckId, UUID jottingId) {
    jottingRepository.delete(deckId, jottingId);
  }
}
