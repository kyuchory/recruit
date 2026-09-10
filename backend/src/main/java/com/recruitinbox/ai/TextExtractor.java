package com.recruitinbox.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills the fields the rule parser could not resolve. The page text is passed as
 * DATA; the implementation must not let it act as instructions and must not
 * invent years/times or extract candidate outcomes (v1.1 section 8.3).
 * Every implementation records usage before returning.
 */
public interface TextExtractor {

    Result extract(Request request);

    record Request(
            UUID runId,
            UUID ownerId,
            String sourceUrl,
            String cleanedText,
            List<String> missingFields,
            String timezoneHint) {
    }

    record Result(
            Map<String, Object> fields,
            List<String> warnings,
            Usage usage) {

        public static Result empty(String warning) {
            return new Result(Map.of(), warning == null ? List.of() : List.of(warning), Usage.none());
        }
    }

    record Usage(String model, int inputTokens, int outputTokens, boolean called) {
        public static Usage none() {
            return new Usage(null, 0, 0, false);
        }
    }
}
