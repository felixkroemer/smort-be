package com.felixkroemer.smort.domain.anki.mapping;

import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.DerivedNoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.AnalysisKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DerivedNoteEntityMapper {

  @Mapping(target = "noteId", source = "noteId")
  @Mapping(target = "front", source = "noteSchema.front")
  @Mapping(target = "back", source = "noteSchema.back")
  @Mapping(target = "content", ignore = true)
  @Mapping(target = "lastFormattedAt", source = "lastFormattedAt")
  @Mapping(target = "pk", source = "analysisId", qualifiedByName = "notePk")
  @Mapping(target = "sk", source = "noteId", qualifiedByName = "noteSk")
  DerivedNoteEntity toDerivedNoteEntity(
      UUID analysisId, Long noteId, NoteSchema noteSchema, Optional<Instant> lastFormattedAt);

  default DerivedNoteEntity toDerivedNoteEntity(
      UUID analysisId, Long noteId, NoteSchema noteSchema) {
    return toDerivedNoteEntity(analysisId, noteId, noteSchema, Optional.of(Instant.now()));
  }

  @Named("notePk")
  default String toNotePk(UUID analysisId) {
    return AnalysisKeys.analysisPk(analysisId);
  }

  @Named("noteSk")
  default String toNoteSk(Long noteId) {
    return NoteKeys.noteSk(noteId);
  }
}
