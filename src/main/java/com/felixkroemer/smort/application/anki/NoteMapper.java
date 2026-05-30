package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.DeckResponse;
import com.felixkroemer.smort.application.anki.dto.DerivedNoteResponse;
import com.felixkroemer.smort.application.anki.dto.NoteResponse;
import com.felixkroemer.smort.domain.anki.Note;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.sqlite.anki.DeckEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteMapper {

  List<DerivedNoteResponse> toDerivedNoteResponseDto(List<DerivedNoteEntity> entities);

  @Mapping(source = "noteId", target = "id")
  DerivedNoteResponse toDerivedNoteResponseDto(DerivedNoteEntity derivedNoteEntity);

  List<NoteResponse> toNoteResponseDto(List<Note> entity);

  NoteResponse toNoteResponseDto(Note entity);

  List<DeckResponse> toDeckResponseDto(List<DeckEntity> entities);

  DeckResponse toDeckResponseDto(DeckEntity entity);
}
