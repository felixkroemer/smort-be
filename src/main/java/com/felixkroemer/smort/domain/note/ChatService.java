package com.felixkroemer.smort.domain.note;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

  @Value("${openai.model}")
  private String model;

  public static class NotesList {
    public List<String> notes;
  }

  private final String FORMATTING_INSTRUCTION =
      """
      Your task is to format each input field individually without changing its content.
      The number of input fields must equal the number of output fields.
      Disregard any format that the field may already be in (e.g. HTML). Consider only its content.
      Format according to the following rules:
      %s
      """;

  private final String FORMATTING_RULES =
      """
      Format as markdown.
      Fix any obvious spelling/punctuation mistakes as long as the intended meaning remains the same.
      """;

  private final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in fact-checking, learning about, and improving the anki note provided in the form of its fields.
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
          .flatMap(notesList -> notesList.notes.stream())
          .toList();
    } catch (Exception e) {
      throw new SmortException("Could not format note", e);
    }
  }

  public ChatMessageResponse chat(
      List<String> fields, String message, Optional<String> previousResponseId) {
    String fullInput = "Fields:\n" + String.join("\n", fields) + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS)
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .build();

    var response = openAIClient.responses().create(params);
    ResponseOutputText outputText = getResponseOutputText(response);

    return new ChatMessageTextResponse(
        outputText.text(),
        new ChatMessageResponseMeta(
            response.id(),
            response.previousResponseId(),
            Instant.now())); // TODO: change to time absed on record
  }

  // TODO: convert to generic getContent
  private static ResponseOutputText getResponseOutputText(Response response) {
    var output = response.output();

    ResponseOutputMessage responseOutputMessage =
        output.stream()
            .flatMap(item -> item.message().stream())
            .reduce(
                (a, b) -> {
                  throw new SmortException("Received multiple outputs");
                })
            .orElseThrow(() -> new SmortException("Received a non-message output"));

    if (responseOutputMessage.content().size() != 1) {
      throw new SmortException(
          "Received multiple contents for a ResponseOutputMessage: {}",
          responseOutputMessage.content().size());
    }

    var content = responseOutputMessage.content().getFirst();

    if (content.isRefusal()) {
      var refusal = content.asRefusal();
      throw new SmortException("Model returned a refusal: {}", refusal.refusal());
    }

    return content
        .outputText()
        .orElseThrow(() -> new SmortException("Expected output_text, got unknown content"));
  }
}
