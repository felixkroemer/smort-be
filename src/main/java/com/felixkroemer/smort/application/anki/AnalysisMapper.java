package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.AnalysisResponse;
import com.felixkroemer.smort.infrastructure.postgres.anki.AnalysisEntity;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnalysisMapper {

  AnalysisResponse toAnalysisResponse(AnalysisEntity entity);

  List<AnalysisResponse> toAnalysisResponse(List<AnalysisEntity> entity);

  default Optional<String> longToOptionalString(Long value) {
    return Optional.ofNullable(value).map(String::valueOf);
  }
}
