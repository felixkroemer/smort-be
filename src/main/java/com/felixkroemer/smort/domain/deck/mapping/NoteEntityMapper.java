package com.felixkroemer.smort.domain.deck.mapping;

import com.felixkroemer.smort.domain.common.NoteSchema;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.NoteEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.partition.DeckKeys;
import com.felixkroemer.smort.infrastructure.dynamodb.keys.sort.NoteKeys;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteEntityMapper {

  @Mapping(target = "id", source = "noteId")
  @Mapping(target = "front", source = "noteSchema.front")
  @Mapping(target = "back", source = "noteSchema.back")
  @Mapping(target = "content", ignore = true)
  @Mapping(target = "pk", source = "deckId", qualifiedByName = "notePk")
  @Mapping(target = "sk", source = "noteId", qualifiedByName = "noteSk")
  NoteEntity toNoteEntity(UUID deckId, UUID noteId, NoteSchema noteSchema);

  @Named("notePk")
  default String toNotePk(UUID deckId) {
    return DeckKeys.deckPk(deckId);
  }

  @Named("noteSk")
  default String toNoteSk(UUID noteId) {
    return NoteKeys.noteSk(noteId);
  }
}
