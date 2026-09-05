package com.felixkroemer.smort.application.anki.mapping;

import com.felixkroemer.smort.application.anki.dto.AnkiDeckResponse;
import com.felixkroemer.smort.application.anki.dto.AnkiNoteResponse;
import com.felixkroemer.smort.application.anki.dto.DerivedNoteResponse;
import com.felixkroemer.smort.domain.anki.AnkiNote;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiDeckEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnkiNoteRestMapper {

  List<DerivedNoteResponse> toDerivedNoteResponse(List<DerivedNoteEntity> entities);

  @Mapping(source = "noteId", target = "id")
  DerivedNoteResponse toDerivedNoteResponse(DerivedNoteEntity derivedNoteEntity);

  List<AnkiNoteResponse> toAnkiNoteResponse(List<AnkiNote> entity);

  @Mapping(source = "content", target = "flds")
  AnkiNoteResponse toAnkiNoteResponse(AnkiNote entity);

  List<AnkiDeckResponse> toAnkiDeckResponse(List<AnkiDeckEntity> entities);

  AnkiDeckResponse toAnkiDeckResponse(AnkiDeckEntity entity);

  default Instant map(Optional<Instant> value) {
    return value.orElse(null);
  }
}
