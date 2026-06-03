package com.felixkroemer.smort.application.deck;

import com.felixkroemer.smort.application.deck.dto.DeckResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckMapper {
    
    DeckResponse toDto(DeckMetaEntity deckMetaEntity);

    List<DeckResponse> toDto(List<DeckMetaEntity> deckMetaEntity);
}
