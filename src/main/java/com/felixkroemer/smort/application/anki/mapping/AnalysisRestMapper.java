package com.felixkroemer.smort.application.anki.mapping;

import com.felixkroemer.smort.application.anki.dto.AnalysisResponse;
import com.felixkroemer.smort.domain.anki.Analysis;
import java.util.List;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnalysisRestMapper {

  @Mapping(source = "analysisId", target = "id")
  AnalysisResponse toAnalysisResponse(Analysis analysis);

  List<AnalysisResponse> toAnalysisResponse(List<Analysis> analysis);

  default Optional<String> longToOptionalString(Long value) {
    return Optional.ofNullable(value).map(String::valueOf);
  }
}
