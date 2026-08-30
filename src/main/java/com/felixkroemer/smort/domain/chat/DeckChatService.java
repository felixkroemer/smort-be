package com.felixkroemer.smort.domain.chat;

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
public class DeckChatService {

  @Value("${openai.model}")
  private String model;

  private final OpenAIClient openAIClient;

  private static final String CHAT_INSTRUCTIONS =
      """
      Your task is to assist the user in learning about and improving their Anki deck.
      You can discuss the deck's content, help identify gaps, and suggest improvements.

      When the user asks you to draft a new note for the deck, use the DraftNote tool.
      The "front" should be the question or term, the "back" the answer or explanation.
      Take the conversation into account: if the topic was discussed before, or the user asked
      for clarifications or adjustments, reflect that in the note, but keep it concise,
      not overly verbose.

      For the formatting, consider these rules:
      %s

      The deck currently contains these notes:
      %s
      """;

  public ChatMessage chat(
      DeckChatContext ctx, String message, Optional<String> previousResponseId) {
    String fullInput = "Deck: " + ctx.deckName() + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(
                CHAT_INSTRUCTIONS.formatted(
                    ChatUtil.formattingRules(), String.join("\n", ctx.notes())))
            .input(fullInput)
            .previousResponseId(previousResponseId)
            .model(model)
            .addTool(DeckChatTools.DraftNoteTool.class)
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
      var toolType = DeckChatToolType.fromToolName(responseFunctionToolCall.name());
      switch (toolType) {
        case DRAFT_NOTE -> {
          var draftNoteToolCall =
              responseFunctionToolCall.arguments(DeckChatTools.DraftNoteTool.class);
          return new DraftNoteToolChatMessage(
              responseFunctionToolCall.callId(),
              draftNoteToolCall.front,
              draftNoteToolCall.back,
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

  public ChatMessage acknowledgeDraftNoteToolCall(String callId, String previousResponseId) {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formattingRules(), ""))
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
}
