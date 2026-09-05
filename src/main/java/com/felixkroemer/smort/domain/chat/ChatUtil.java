package com.felixkroemer.smort.domain.chat;

import com.felixkroemer.smort.common.exception.SmortException;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;

public final class ChatUtil {

  private ChatUtil() {}

  public static String formatInstructions(String customInstructions) {
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
        .formatted(customInstructions);
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
