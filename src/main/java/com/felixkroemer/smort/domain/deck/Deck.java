package com.felixkroemer.smort.domain.deck;

import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Deck {
  private UUID deckId;
  private String name;
  private String userId;
  private DeckStatus status;
  private Optional<BulkFormat> bulkFormat = Optional.empty();
  private Optional<DraftNote> draftNote = Optional.empty();
}
