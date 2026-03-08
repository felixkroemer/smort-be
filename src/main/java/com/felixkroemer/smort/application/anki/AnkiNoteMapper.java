package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.AnkiNoteResponse;
import com.felixkroemer.smort.infrastructure.sqlite.anki.AnkiNoteEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnkiNoteMapper {

  List<AnkiNoteResponse> toDto(List<AnkiNoteEntity> entities);

  AnkiNoteResponse toDto(AnkiNoteEntity entity);
}
