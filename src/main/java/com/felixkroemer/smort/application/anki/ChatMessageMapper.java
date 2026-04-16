package com.felixkroemer.smort.application.anki;

import com.felixkroemer.smort.application.anki.dto.ChatMessageResponseDTO;
import com.felixkroemer.smort.infrastructure.dynamodb.anki.ChatMessageResponseEntity;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMessageMapper {

  List<ChatMessageResponseDTO> toDto(List<ChatMessageResponseEntity> chatMessageEntities);

  ChatMessageResponseDTO toDto(ChatMessageResponseEntity chatMessageResponseEntity);
}
