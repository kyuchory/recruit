package com.recruitinbox.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vision fallback for information that only appears in a job-posting image
 * (deadline, role, steps). Same guarantees as {@link TextExtractor}. Used only
 * after text/DOM could not resolve a field (v1.1 section 8.3).
 */
public interface ImageExtractor {

    TextExtractor.Result extract(Request request);

    record Request(
            UUID runId,
            UUID ownerId,
            List<ImagePart> images,
            List<String> missingFields,
            String timezoneHint) {
    }

    record ImagePart(String mimeType, byte[] bytes) {
    }

    static TextExtractor.Result disabled() {
        return new TextExtractor.Result(Map.of(), List.of("VISION_DISABLED"), TextExtractor.Usage.none());
    }
}
