package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.DeckResponse;
import com.felixkroemer.smort.application.anki.dto.NoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.DeckEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.NoteEntity;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteMapper {

  List<NoteResponse> toNoteResponseDto(List<NoteEntity> entities);

  NoteResponse toNoteResponseDto(NoteEntity entity);

  @Mapping(source = "sourceNoteId", target = "id")
  NoteResponse toNoteResponseDto(DerivedNoteEntity derivedNoteEntity);

  List<DeckResponse> toDeckResponseDto(List<DeckEntity> entities);

  DeckResponse toDeckResponseDto(DeckEntity entity);
}
