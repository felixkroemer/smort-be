package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.DraftNoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.DraftNoteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DraftNoteRestMapper {

  DraftNoteResponse toDraftNoteResponse(DraftNoteEntity draftNoteEntity);
}