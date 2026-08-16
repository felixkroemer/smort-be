package com.felixkroemer.smort.application.anki.mapping;

import com.felixkroemer.smort.application.anki.dto.BulkFormatResponse;
import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatStatus;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BulkFormatRestMapper {

  BulkFormatResponse toBulkFormatResponse(BulkFormat bulkFormat);

  default String map(BulkFormatStatus status) {
    return status.name();
  }
}
