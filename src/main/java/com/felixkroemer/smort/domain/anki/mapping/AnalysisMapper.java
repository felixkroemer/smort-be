package com.felixkroemer.smort.domain.anki.mapping;

import com.felixkroemer.smort.domain.anki.Analysis;
import com.felixkroemer.smort.domain.anki.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.AnalysisMetaEntity;
import java.nio.file.Path;
import java.util.Optional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnalysisMapper {

  Analysis toAnalysis(AnalysisMetaEntity meta, Optional<BulkFormat> bulkFormat);

  default Path toPath(String value) {
    return value != null ? Path.of(value) : null;
  }
}
