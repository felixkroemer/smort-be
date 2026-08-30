package com.felixkroemer.smort.domain.chat;

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

      The ankiNote has these fields:
      %s

      When you are asked to edit one or multiple fields in any way, use the tool for updating notes.
      Then acknowledge with a short summary.

      For the formatting, consider these rules:
      %s
      """;

  private static final String NOTE_ACK_INSTRUCTIONS =
      """
      Confirm to the user that the note was updated. Keep it to one short sentence.
      """;

  public StoreNoteToolChatMessage formatNote(
      Map<String, String> fields,
      Optional<String> formatInstructions,
      Optional<String> userActionContext) {
    try {
      StructuredResponseCreateParams<NoteSchema> params =
          ResponseCreateParams.builder()
              .instructions(
                  ChatUtil.appendUserActions(
                      ChatUtil.formatInstructions(formatInstructions), userActionContext))
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
          "",
          content.front(),
          content.back(),
          new ChatMessageMeta(response.id(), Optional.empty(), Instant.now()));
    } catch (Exception e) {
      throw new SmortException("Could not format ankiNote", e);
    }
  }

  public ChatMessage acknowledgeStoreNoteToolCall(String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(NOTE_ACK_INSTRUCTIONS)
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
      NoteChatContext<?> ctx,
      String message,
      Optional<String> formatInstructions,
      Optional<String> previousResponseId,
      Optional<String> userActionContext) {
    String fieldsBlock =
        String.join(
            "\n",
            ctx.fields().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .toList());

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                ChatUtil.appendUserActions(
                    CHAT_INSTRUCTIONS.formatted(
                        fieldsBlock, ChatUtil.formatInstructions(formatInstructions)),
                    userActionContext))
            .input(message)
            .previousResponseId(previousResponseId)
            .model(model)
            .addTool(NoteChatTools.StoreNoteTool.class)
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
      var toolType = NoteChatToolType.fromToolName(responseFunctionToolCall.name());
      switch (toolType) {
        case STORE_NOTE -> {
          var storeNoteToolCall =
              responseFunctionToolCall.arguments(NoteChatTools.StoreNoteTool.class);
          return new StoreNoteToolChatMessage(
              responseFunctionToolCall.callId(),
              storeNoteToolCall.front,
              storeNoteToolCall.back,
              meta);
        }
        default ->
            throw new SmortException(
                "Unexpected tool called. toolName={}", responseFunctionToolCall.name());
      }

    } else if (responseOutputItem.isMessage()) {
      ResponseOutputText outputText =
          ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
      return new TextChatMessage(outputText.text(), meta);
    } else {
      throw new SmortException("Unexpected response output item type");
    }
  }
}
