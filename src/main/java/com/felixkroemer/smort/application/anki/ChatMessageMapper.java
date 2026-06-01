package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.ChatMessageResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageResponseEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMessageMapper {

  List<ChatMessageResponse> toDto(List<ChatMessageResponseEntity> chatMessageEntities);

  ChatMessageResponse toDto(ChatMessageResponseEntity chatMessageResponseEntity);
}
