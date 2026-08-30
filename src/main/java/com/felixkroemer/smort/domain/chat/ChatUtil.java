package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import java.util.Optional;

public final class ChatUtil {

  private ChatUtil() {}

  public static String formatInstructions() {
    return formatInstructions(Optional.empty());
  }

  public static String formatInstructions(Optional<String> customInstructions) {
    return """
        You receive an Anki ankiNote as a list of fields, each with a title and content.
        Your task is to produce exactly two output fields: "front" and "back".

        Mapping rules:
        - Identify the single field that clearly represents the main question or term (e.g. titled "Front", "Question", "Term", or similar). Map it to "front".
        - Concatenate all remaining fields into "back". When concatenating multiple fields, separate them using their titles to distinguish them.

        When processing each field, consider only its content and intended meaning — disregard any existing formatting entirely.

        Formatting rules (apply to both fields):
        %s
    """
        .formatted(customInstructions.orElse(formattingRules()));
  }

  public static String formattingRules() {
    return """
        Output must be plain markdown. Never output HTML tags — not even a single one.
        Convert all HTML in the input to its markdown equivalent before outputting (e.g. <strong> → **, <ul>/<li> → - lists, <code> → `code`).
        When separating concatenated fields, use markdown headings (e.g. ## Definition, ## Example).
        Fix any obvious spelling and punctuation mistakes as long as the intended meaning remains unchanged.
    """;
  }

  public static ResponseOutputText getResponseOutputText(
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
