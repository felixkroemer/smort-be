package com.felixkroemer.smort.domain.anki.mapping;

import com.felixkroemer.smort.domain.anki.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.BulkFormatEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BulkFormatEntityMapper {

  BulkFormat toBulkFormat(BulkFormatEntity entity);
}
