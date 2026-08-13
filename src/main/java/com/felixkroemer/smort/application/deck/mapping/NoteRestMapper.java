package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteRestMapper {

  NoteResponse toNoteResponse(NoteEntity noteEntity);

  List<NoteResponse> toNoteResponse(List<NoteEntity> noteEntities);
}
