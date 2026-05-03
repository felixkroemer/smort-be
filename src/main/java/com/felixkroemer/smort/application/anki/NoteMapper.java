package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.DeckResponse;
import com.felixkroemer.smort.application.anki.dto.DerivedNoteResponse;
import com.felixkroemer.smort.application.anki.dto.SourceNoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.DeckEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteMapper {

  List<DerivedNoteResponse> toDerivedNoteResponseDto(List<DerivedNoteEntity> entities);

  @Mapping(source = "sourceNoteId", target = "id")
  DerivedNoteResponse toDerivedNoteResponseDto(DerivedNoteEntity derivedNoteEntity);

  List<SourceNoteResponse> toSourceNoteResponseDto(List<NoteEntity> entity);

  SourceNoteResponse toSourceNoteResponseDto(NoteEntity entity);

  List<DeckResponse> toDeckResponseDto(List<DeckEntity> entities);

  DeckResponse toDeckResponseDto(DeckEntity entity);
}
