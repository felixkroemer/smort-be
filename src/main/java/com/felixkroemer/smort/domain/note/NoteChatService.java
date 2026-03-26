package com.felixkroemer.smort.domain.note;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteChatService {

  @Value("${openai.model}")
  private String model;

  public static class NotesList {
    public List<String> notes;
  }

  private final String FORMATTING_INSTRUCTION =
      """
    Format the input according to the following rules:
    %s
    """;

  private final String FORMATTING_RULES =
      """
    Format as markdown. Use bullet points where possible.
    """;

  private final OpenAIClient openAIClient;
  private final ObjectMapper mapper;

  public List<String> formatNote(List<String> fields) {
    try {
      StructuredResponseCreateParams<NotesList> params =
          ResponseCreateParams.builder()
              .instructions(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES))
              .input(mapper.writeValueAsString(fields))
              .text(NotesList.class)
              .model(model)
              .build();

      return openAIClient.responses().create(params).output().stream()
          .flatMap(item -> item.message().stream())
          .flatMap(message -> message.content().stream())
          .flatMap(content -> content.outputText().stream())
          .flatMap(bookList -> bookList.notes.stream())
          .toList();
    } catch (Exception e) {
      throw new SmortException("Could not format note", e);
    }
  }
}
