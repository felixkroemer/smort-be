package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

  @Value("${openai.model}")
  private String model;

  @Getter
  public static class NoteSchema {
    public String front;
    public String back;
  }

  private final String FORMATTING_INSTRUCTION =
      """
          You receive an Anki ankiNote as a list of fields, each with a title and content.
          Your task is to produce exactly two output fields: "front" and "back".

          Mapping rules:
          - Identify the single field that clearly represents the main question or term (e.g. titled "Front", "Question", "Term", or similar). Map it to "front".
          - Concatenate all remaining fields into "back". When concatenating multiple fields, separate them using their titles to distinguish them.

          When processing each field, consider only its content and intended meaning — disregard any existing formatting entirely.

          Formatting rules (apply to both fields):
          %s
      """;

  private final String FORMATTING_RULES =
      """
          Output must be plain markdown. Never output HTML tags — not even a single one.
          Convert all HTML in the input to its markdown equivalent before outputting (e.g. <strong> → **, <ul>/<li> → - lists, <code> → `code`).
          When separating concatenated fields, use markdown headings (e.g. ## Definition, ## Example).
          Fix any obvious spelling and punctuation mistakes as long as the intended meaning remains unchanged.
      """;

  private final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in fact-checking, learning about, and improving the anki ankiNote provided in the form of its fields.
      When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
      Then acknowledge with a short summary.

      For the formatting, consider these rules:
      %s
      """;

  private final OpenAIClient openAIClient;
  private final ObjectMapper mapper;

  public NoteSchema formatNote(String front, String back) {
    return formatNote(Map.of("front", front, "back", back));
  }

  public NoteSchema formatNote(Map<String, String> fields) {
    try {
      StructuredResponseCreateParams<NoteSchema> params =
          ResponseCreateParams.builder()
              .instructions(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES))
              .input(mapper.writeValueAsString(fields))
              .text(NoteSchema.class)
              .model(model)
              .build();

      return openAIClient.responses().create(params).output().stream()
          .flatMap(item -> item.message().stream())
          .flatMap(message -> message.content().stream())
          .flatMap(content -> content.outputText().stream())
          .findFirst()
          .orElseThrow();
    } catch (Exception e) {
      throw new SmortException("Could not format ankiNote", e);
    }
  }

  @JsonClassDescription("Store a updated ankiNote.")
  static class StoreNoteTool {
    public String front;
    public String back;
  }

  public ChatMessageResponse acknowledgeStoreNoteToolCall(
      String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES)))
            .input(
                ResponseCreateParams.Input.ofResponse(
                    List.of(
                        ResponseInputItem.ofFunctionCallOutput(
                            ResponseInputItem.FunctionCallOutput.builder()
                                .callId(callId)
                                .outputAsJson("ok")
                                .build()))))
            .previousResponseId(previousResponseId)
            .model(model)
            .build();

    var response = openAIClient.responses().create(params);
    var output = response.output();

    var responseOutputItem =
        output.stream()
            .reduce(
                (a, b) -> {
                  throw new SmortException("Received multiple output items");
                })
            .orElseThrow(() -> new SmortException("Received no output items"));

    var meta =
        new ChatMessageResponseMeta(response.id(), response.previousResponseId(), Instant.now());
    ResponseOutputText outputText = getResponseOutputText(responseOutputItem.asMessage());
    return new ChatMessageTextResponse(outputText.text(), meta);
  }

  public ChatMessageResponse chat(
      String front, String back, String message, Optional<String> previousResponseId) {
    var content = Map.of("front", front, "back", back);
    return chat(content, message, previousResponseId);
  }

  public ChatMessageResponse chat(
      Map<String, String> fields, String message, Optional<String> previousResponseId) {
    String fullInput =
        "Fields:\n"
            + String.join(
                "\n",
                fields.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList())
            + "\n\n"
            + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(FORMATTING_INSTRUCTION.formatted(FORMATTING_RULES)))
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .addTool(StoreNoteTool.class)
            .build();

    var response = openAIClient.responses().create(params);
    var output = response.output();

    var responseOutputItem =
        output.stream()
            .reduce(
                (a, b) -> {
                  throw new SmortException("Received multiple output items");
                })
            .orElseThrow(() -> new SmortException("Received no output items"));

    var meta =
        new ChatMessageResponseMeta(response.id(), response.previousResponseId(), Instant.now());

    if (responseOutputItem.isFunctionCall()) {
      var responseFunctionToolCall = responseOutputItem.asFunctionCall();
      var storeNoteToolCall = responseFunctionToolCall.arguments(StoreNoteTool.class);
      return new StoreNoteToolResponse(
          StoreNoteTool.class.getName(),
          responseFunctionToolCall.callId(),
          storeNoteToolCall.front,
          storeNoteToolCall.back,
          meta);
    } else if (responseOutputItem.isMessage()) {
      ResponseOutputText outputText = getResponseOutputText(responseOutputItem.asMessage());
      return new ChatMessageTextResponse(outputText.text(), meta);
    } else {
      throw new SmortException("Unexpected response output item type");
    }
  }

  private static ResponseOutputText getResponseOutputText(
      ResponseOutputMessage responseOutputMessage) {
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
