package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatMessageEntity;
import com.felixkroemer.smort.infrastructure.dynamodb.chat.ChatRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserActionContextService {

  private final ChatRepository chatRepository;
  private final ObjectMapper mapper;

  public <T> Optional<String> buildContext(String pk, T entityId) {
    var messages = chatRepository.findAll(pk, entityId);

    var consecutiveRun = new ArrayList<ChatMessageEntity>();
    for (var message : messages) {
      if (!message.isUserInitiated()) {
        break;
      }
      consecutiveRun.add(message);
    }

    if (consecutiveRun.isEmpty()) {
      return Optional.empty();
    }

    var entries =
        consecutiveRun.reversed().stream()
            .filter(m -> m.getToolName().isPresent())
            .map(
                m ->
                    Map.<String, Object>of(
                        "toolName", m.getToolName().get(), "arguments", m.getArguments()))
            .toList();

    if (entries.isEmpty()) {
      return Optional.empty();
    }

    try {
      return Optional.of("Recent user actions:\n" + mapper.writeValueAsString(entries));
    } catch (JsonProcessingException e) {
      throw new SmortException("Could not serialize user action context", e);
    }
  }
}
