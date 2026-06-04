package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.AnkiNoteResponse;
import com.felixkroemer.smort.application.anki.dto.DeckResponse;
import com.felixkroemer.smort.application.anki.dto.DerivedNoteResponse;
import com.felixkroemer.smort.domain.anki.AnalysisNote;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiDeckEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnkiNoteMapper {

  List<DerivedNoteResponse> toDerivedNoteResponseDto(List<DerivedNoteEntity> entities);

  @Mapping(source = "noteId", target = "id")
  DerivedNoteResponse toDerivedNoteResponseDto(DerivedNoteEntity derivedNoteEntity);

  List<AnkiNoteResponse> toNoteResponseDto(List<AnalysisNote> entity);

  AnkiNoteResponse toNoteResponseDto(AnalysisNote entity);

  List<DeckResponse> toDeckResponseDto(List<AnkiDeckEntity> entities);

  DeckResponse toDeckResponseDto(AnkiDeckEntity entity);
}
