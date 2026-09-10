package com.recruitinbox.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default wiring: {@code ai.enabled} unset or false -> no model is ever called.
 * The HTML pipeline (Step 10) is the whole analysis.
 */
@Configuration
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "false", matchIfMissing = true)
class DisabledExtractors {

    @Bean
    TextExtractor disabledTextExtractor() {
        return request -> TextExtractor.Result.empty("AI_DISABLED");
    }

    @Bean
    ImageExtractor disabledImageExtractor() {
        return request -> ImageExtractor.disabled();
    }
}
