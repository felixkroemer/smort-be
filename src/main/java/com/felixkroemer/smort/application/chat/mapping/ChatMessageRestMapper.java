package com.felixkroemer.smort.application.chat.mapping;

import com.felixkroemer.smort.application.chat.dto.ChatMessageResponse;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMessageRestMapper {

  List<ChatMessageResponse> toChatMessageResponse(List<ChatMessageEntity> chatMessageEntities);

  ChatMessageResponse toChatMessageResponse(ChatMessageEntity chatMessageEntity);
}
