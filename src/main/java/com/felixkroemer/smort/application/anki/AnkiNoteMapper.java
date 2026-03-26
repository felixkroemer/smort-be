package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.DeckResponse;
import com.felixkroemer.smort.application.anki.dto.NoteResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.analysis.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiDeckEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnkiNoteMapper {

  List<NoteResponse> toNoteResponseDto(List<AnkiNoteEntity> entities);

  NoteResponse toNoteResponseDto(AnkiNoteEntity entity);

  @Mapping(source = "sourceNoteId", target = "id")
  NoteResponse toNoteResponseDto(DerivedNoteEntity derivedNoteEntity);

  List<DeckResponse> toDeckResponseDto(List<AnkiDeckEntity> entities);

  DeckResponse toDeckResponseDto(AnkiDeckEntity entity);
}
