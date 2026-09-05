package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.NoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteRestMapper {

  NoteResponse toNoteResponse(NoteEntity noteEntity);

  List<NoteResponse> toNoteResponse(List<NoteEntity> noteEntities);

  default Instant map(Optional<Instant> value) {
    return value.orElse(null);
  }
}
