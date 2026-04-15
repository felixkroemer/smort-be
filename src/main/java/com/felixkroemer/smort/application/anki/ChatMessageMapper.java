package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.ChatMessageResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.ChatMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMessageMapper {

  ChatMessageResponse toDto(ChatMessageEntity chatMessageEntity);
}
