package com.recruitinbox.common.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.recruitinbox.support.AbstractIntegrationTest;

/**
 * Locks the auth-failure contract (design v1.1 section 7.4):
 * <ul>
 *   <li>no / unknown / malformed identity -&gt; 401 {@code UNAUTHENTICATED}</li>
 *   <li>a resource the caller may not see -&gt; 404 {@code NOT_FOUND} (no 403,
 *       no existence leak)</li>
 * </ul>
 * The 403 {@code FORBIDDEN} path (CSRF) is covered by {@code CsrfProtectionTest}.
 * All go through the shared {@code ErrorResponse} envelope.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthResponseTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void unknownIdentityIs401() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Dev-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.error.retryable", is(false)))
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    void malformedIdentityIs401() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Dev-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHENTICATED")));
    }

    @Test
    void missingResourceIs404NotForbidden() throws Exception {
        mvc.perform(get("/api/v1/applications/{id}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }
}
