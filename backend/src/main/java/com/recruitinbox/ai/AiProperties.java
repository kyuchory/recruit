package com.recruitinbox.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ai.*}. With {@code ai.enabled=false} (default) only the rule-based HTML
 * parser runs; nothing calls a model. Real keys arrive via env
 * ({@code OPENAI_API_KEY} -> {@code ai.openai.api-key}).
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String textModel,
        String visionModel,
        int maxInputTokens,
        int maxOutputTokens,
        int requestTimeoutMs,
        OpenAi openai) {

    public AiProperties {
        provider = provider == null || provider.isBlank() ? "openai" : provider;
        textModel = textModel == null || textModel.isBlank() ? "gpt-4o-mini" : textModel;
        visionModel = visionModel == null || visionModel.isBlank() ? "gpt-4o-mini" : visionModel;
        maxInputTokens = maxInputTokens <= 0 ? 6000 : maxInputTokens;
        maxOutputTokens = maxOutputTokens <= 0 ? 1200 : maxOutputTokens;
        requestTimeoutMs = requestTimeoutMs <= 0 ? 45_000 : requestTimeoutMs;
        openai = openai == null ? new OpenAi(null, "https://api.openai.com/v1") : openai;
    }

    public record OpenAi(String apiKey, String baseUrl) {
        public OpenAi {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        }
    }
}
