package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.domain.deck.Deck;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckRestMapper {

  DeckResponse toDeckResponse(Deck deck);

  List<DeckResponse> toDeckResponse(List<Deck> decks);
}
