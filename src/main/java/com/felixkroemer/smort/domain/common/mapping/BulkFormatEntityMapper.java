package com.felixkroemer.smort.domain.common.mapping;

import com.felixkroemer.smort.domain.common.BulkFormat;
import com.felixkroemer.smort.infrastructure.dynamodb.BulkFormatEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BulkFormatEntityMapper {

  BulkFormat toBulkFormat(BulkFormatEntity entity);
}
