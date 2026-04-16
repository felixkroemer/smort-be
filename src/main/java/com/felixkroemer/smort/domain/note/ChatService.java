package com.felixkroemer.smort.domain.note;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
      When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
      Then acknowledge with a short summary.
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

  @JsonClassDescription("Store a updated note.")
  static class StoreNoteTool {
    @JsonPropertyDescription("The notes fields.")
    public List<String> fields;

    public void execute() {}
  }

  public ChatMessageResponse acknowledgeStoreNoteToolCall(
      String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS)
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
      List<String> fields, String message, Optional<String> previousResponseId) {
    String fullInput = "Fields:\n" + String.join("\n", fields) + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS)
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
          storeNoteToolCall.fields,
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
