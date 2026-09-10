package com.recruitinbox.parser;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration
class NoopExtractionQueueConfig {

    @Bean
    @ConditionalOnMissingBean(ExtractionQueue.class)
    ExtractionQueue noopExtractionQueue() {
        return (UUID runId) -> {
            // Step 9 replaces this with an after-commit async dispatch.
        };
    }
}
