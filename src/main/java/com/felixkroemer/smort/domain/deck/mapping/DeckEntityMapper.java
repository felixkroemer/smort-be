package com.felixkroemer.smort.domain.deck.mapping;

import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.domain.deck.Deck;
import com.felixkroemer.smort.domain.deck.DraftNote;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DeckMetaEntity;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeckEntityMapper {

  Deck toDeck(DeckMetaEntity meta, Optional<BulkFormat> bulkFormat, Optional<DraftNote> draftNote);
}
