package com.recruitinbox.parser.html;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.recruitinbox.common.error.ApiException;

/**
 * JDK HttpClient with redirects OFF -- each hop is validated by
 * {@link UrlSafetyValidator} before it is followed (v1.1 section 12.1).
 * Enforces a body cap and connect/read timeouts.
 */
@Component
public class HttpJobPageFetcher implements JobPageFetcher {

    private final UrlSafetyValidator safety;
    private final HttpClient client;
    private final int maxRedirects;
    private final long maxBodyBytes;
    private final Duration requestTimeout;

    public HttpJobPageFetcher(
            UrlSafetyValidator safety,
            @Value("${parser.fetch.connect-timeout-ms:5000}") long connectMs,
            @Value("${parser.fetch.request-timeout-ms:10000}") long requestMs,
            @Value("${parser.fetch.max-redirects:3}") int maxRedirects,
            @Value("${parser.fetch.max-body-bytes:2097152}") long maxBodyBytes) {
        this.safety = safety;
        this.maxRedirects = maxRedirects;
        this.maxBodyBytes = maxBodyBytes;
        this.requestTimeout = Duration.ofMillis(requestMs);
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
    }

    @Override
    public FetchResult fetch(String url) {
        String current = url;
        for (int hop = 0; hop <= maxRedirects; hop++) {
            URI uri = URI.create(current);
            try {
                safety.assertSafe(uri);
            } catch (ApiException e) {
                throw new FetchException("UNSAFE_URL", false, e.getMessage());
            }

            HttpResponse<byte[]> res;
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(requestTimeout)
                        .header("User-Agent", "recruit-inbox-bot/0.1 (+https://recruit-inbox.local)")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .GET()
                        .build();
                res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (HttpTimeoutException e) {
                throw new FetchException("FETCH_TIMEOUT", true, "request timed out");
            } catch (IOException e) {
                throw new FetchException("FETCH_ERROR", true, "network error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("FETCH_ERROR", true, "interrupted");
            }

            int sc = res.statusCode();
            if (sc >= 300 && sc < 400) {
                String loc = res.headers().firstValue("location").orElse(null);
                if (loc == null) {
                    throw new FetchException("FETCH_ERROR", false, "redirect without Location");
                }
                current = uri.resolve(loc).toString();
                continue;
            }
            if (sc == 401 || sc == 403 || sc == 407 || sc == 429) {
                throw new FetchException("FETCH_BLOCKED", false, "source returned " + sc);
            }
            if (sc >= 500) {
                throw new FetchException("FETCH_ERROR", true, "source returned " + sc);
            }
            if (sc != 200) {
                throw new FetchException("FETCH_ERROR", false, "unexpected status " + sc);
            }

            String contentType = res.headers().firstValue("content-type").orElse("");
            if (!contentType.isBlank() && !contentType.toLowerCase().contains("html")
                    && !contentType.toLowerCase().contains("xml")) {
                throw new FetchException("FETCH_NOT_HTML", false, "content-type " + contentType);
            }
            byte[] body = res.body();
            if (body.length > maxBodyBytes) {
                throw new FetchException("FETCH_TOO_LARGE", false, "body exceeds " + maxBodyBytes + " bytes");
            }
            return new FetchResult(uri.toString(), new String(body, StandardCharsets.UTF_8), contentType);
        }
        throw new FetchException("FETCH_ERROR", false, "too many redirects");
    }
}
