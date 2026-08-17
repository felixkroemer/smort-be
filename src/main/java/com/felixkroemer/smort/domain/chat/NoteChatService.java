package com.felixkroemer.smort.domain.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.felixkroemer.smort.common.exception.SmortException;
import com.felixkroemer.smort.domain.common.NoteSchema;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteChatService {

  @Value("${openai.model}")
  private String model;

  private final OpenAIClient openAIClient;
  private final ObjectMapper mapper;

  private static final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in fact-checking, learning about, and improving the anki ankiNote provided in the form of its fields.
      When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
      Then acknowledge with a short summary.

      For the formatting, consider these rules:
      %s
      """;

  public StoreNoteToolChatMessage formatNote(
      Map<String, String> fields, Optional<String> formatInstructions) {
    try {
      StructuredResponseCreateParams<NoteSchema> params =
          ResponseCreateParams.builder()
              .instructions(
                  ChatUtil.formatInstructions())
              .input(mapper.writeValueAsString(fields))
              .text(NoteSchema.class)
              .model(model)
              .build();

      var response = openAIClient.responses().create(params);

      var content =
          response.output().stream()
              .flatMap(item -> item.message().stream())
              .flatMap(message -> message.content().stream())
              .flatMap(c -> c.outputText().stream())
              .findFirst()
              .orElseThrow();

      return new StoreNoteToolChatMessage(
          StoreNoteTool.class.getName(),
          "",
          content.front(),
          content.back(),
          new ChatMessageMeta(response.id(), Optional.empty(), Instant.now()));
    } catch (Exception e) {
      throw new SmortException("Could not format ankiNote", e);
    }
  }

  @JsonClassDescription("Store a updated ankiNote.")
  static class StoreNoteTool {
    public String front;
    public String back;
  }

  public ChatMessage acknowledgeStoreNoteToolCall(String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))
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

    var meta = new ChatMessageMeta(response.id(), response.previousResponseId(), Instant.now());
    ResponseOutputText outputText = ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
    return new TextChatMessage(outputText.text(), meta);
  }

  public ChatMessage chat(
      NoteChatContext ctx, String message, Optional<String> previousResponseId) {
    String fullInput =
        "Fields:\n"
            + String.join(
                "\n",
                ctx.fields().entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList())
            + "\n\n"
            + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))
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

    var meta = new ChatMessageMeta(response.id(), response.previousResponseId(), Instant.now());

    if (responseOutputItem.isFunctionCall()) {
      var responseFunctionToolCall = responseOutputItem.asFunctionCall();
      var storeNoteToolCall = responseFunctionToolCall.arguments(StoreNoteTool.class);
      return new StoreNoteToolChatMessage(
          StoreNoteTool.class.getName(),
          responseFunctionToolCall.callId(),
          storeNoteToolCall.front,
          storeNoteToolCall.back,
          meta);
    } else if (responseOutputItem.isMessage()) {
      ResponseOutputText outputText = ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
      return new TextChatMessage(outputText.text(), meta);
    } else {
      throw new SmortException("Unexpected response output item type");
    }
  }
}
