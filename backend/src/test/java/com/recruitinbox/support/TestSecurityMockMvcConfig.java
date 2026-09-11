package com.recruitinbox.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Makes every MockMvc request carry a valid CSRF token by default, so the
 * existing controller tests keep passing now that {@link com.recruitinbox.common.web.SecurityConfig}
 * enforces CSRF on mutations. Dedicated rejection coverage lives in
 * {@code CsrfProtectionTest} (real HTTP).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityMockMvcConfig {

    @Bean
    MockMvcBuilderCustomizer csrfByDefault() {
        return builder -> builder.defaultRequest(get("/").with(csrf()));
    }
}
