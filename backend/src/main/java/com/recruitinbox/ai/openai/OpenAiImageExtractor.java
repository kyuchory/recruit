package com.recruitinbox.ai.openai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.recruitinbox.ai.ImageExtractor;
import com.recruitinbox.ai.TextExtractor;

/**
 * Vision extractor placeholder for {@code ai.enabled=true}. The real
 * chat-with-image call is wired in Step 12; until then it returns no fields so
 * the pipeline degrades to text/DOM only.
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true")
public class OpenAiImageExtractor implements ImageExtractor {

    @Override
    public TextExtractor.Result extract(Request request) {
        return TextExtractor.Result.empty("VISION_NOT_IMPLEMENTED");
    }
}
