package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import java.time.Instant;
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

      For the formatting, consider these rules:
      %s
      """;

  public ChatMessage chat(
      DeckChatContext ctx, String message, Optional<String> previousResponseId) {
    String fullInput = "Deck: " + ctx.deckName() + "\n\n" + message;

    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .instructions(CHAT_INSTRUCTIONS.formatted(ChatUtil.formatInstructions()))
            .input(fullInput)
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

    if (responseOutputItem.isMessage()) {
      ResponseOutputText outputText = ChatUtil.getResponseOutputText(responseOutputItem.asMessage());
      return new TextChatMessage(outputText.text(), meta);
    } else {
      throw new SmortException("Unexpected response output item type");
    }
  }
}
