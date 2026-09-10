package com.recruitinbox.ai.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.recruitinbox.ai.AiProperties;

/** Real OpenAI transport. Only created when {@code ai.enabled=true}. */
@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class HttpChatTransport implements ChatTransport {

    private final AiProperties props;
    private final HttpClient client;

    public HttpChatTransport(AiProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String postChatCompletions(String jsonBody) {
        String apiKey = props.openai().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.enabled=true but ai.openai.api-key is not set");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.openai().baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(props.requestTimeoutMs()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiCallException("openai returned " + res.statusCode(), res.statusCode() == 429
                        || res.statusCode() >= 500);
            }
            return res.body();
        } catch (IOException e) {
            throw new AiCallException("openai call failed: " + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCallException("openai call interrupted", true);
        }
    }

    public static class AiCallException extends RuntimeException {
        private final boolean retryable;

        public AiCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
