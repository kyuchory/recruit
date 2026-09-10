package com.recruitinbox.ai.openai;

/** Minimal seam over the OpenAI HTTP API so extractors stay provider-agnostic and testable. */
public interface ChatTransport {

    /** @param jsonBody a full chat-completions request body; @return the raw response body */
    String postChatCompletions(String jsonBody);
}
