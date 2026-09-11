package com.recruitinbox.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.recruitinbox.support.AbstractIntegrationTest;

/**
 * Real-HTTP checks that CSRF protection guards browser mutations while leaving
 * safe methods open, and that a rejection uses the shared error envelope.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfProtectionTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Deliberately invalid so a token-accepted request stops at validation (400) and persists nothing. */
    private static final String INVALID_LINK_BODY = "{}";

    @Test
    void mutationWithoutTokenIsForbiddenWithErrorEnvelope() throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/links")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INVALID_LINK_BODY))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.body()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void safeRequestPublishesTokenAndMutationWithItPassesCsrf() throws Exception {
        HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/auth/csrf")))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);

        String setCookie = csrf.headers().firstValue("set-cookie").orElseThrow();
        assertThat(setCookie).contains("XSRF-TOKEN=");
        String cookiePair = setCookie.split(";", 2)[0];
        String token = cookiePair.substring(cookiePair.indexOf('=') + 1);
        assertThat(token).isNotBlank();

        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url("/api/v1/links")))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", token)
                .header("Cookie", cookiePair)
                .POST(HttpRequest.BodyPublishers.ofString(INVALID_LINK_BODY))
                .build(), HttpResponse.BodyHandlers.ofString());

        // Token accepted -> request reaches the controller and fails validation, not CSRF.
        assertThat(res.statusCode()).isNotEqualTo(403);
        assertThat(res.statusCode()).isEqualTo(400);
    }
}
