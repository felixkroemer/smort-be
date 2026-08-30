package com.felixkroemer.smort.domain.deck.mapping;

import com.felixkroemer.smort.domain.deck.DraftNote;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DraftNoteEntityMapper {

  DraftNote toDraftNote(DraftNoteEntity entity);
}
