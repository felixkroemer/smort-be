package com.felixkroemer.smort.application.deck.mapping;

import com.felixkroemer.smort.application.deck.dto.JottingResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.deck.JottingEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface JottingRestMapper {

  JottingResponse toJottingResponse(JottingEntity jottingEntity);

  List<JottingResponse> toJottingResponse(List<JottingEntity> jottingEntities);
}
