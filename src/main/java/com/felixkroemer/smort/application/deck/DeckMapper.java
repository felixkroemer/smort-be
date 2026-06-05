package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckMapper {

  DeckResponse toDto(DeckMetaEntity deckMetaEntity);

  List<DeckResponse> toDto(List<DeckMetaEntity> deckMetaEntity);
}
