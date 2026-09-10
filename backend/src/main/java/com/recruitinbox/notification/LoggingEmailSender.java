package com.recruitinbox.notification;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dev email channel: logs the message and reports ACCEPTED. A real provider
 * adapter replaces this bean by simply being present on the classpath.
 */
@Configuration
class LoggingEmailSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender loggingEmailSender() {
        return (to, subject, body, context) -> {
            log.info("[DEV-EMAIL] to={} subject=\"{}\" body=\"{}\" ctx={}", to, subject, body, context);
            return EmailSender.Result.accepted("dev-" + UUID.randomUUID());
        };
    }

    static Map<String, Object> noContext() {
        return Map.of();
    }
}
