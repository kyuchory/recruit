package com.recruitinbox.link;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/**
 * Syntactic URL validation + conservative normalization (v1.1 section 10.1):
 * lowercase scheme/host, drop the default port and fragment, strip known
 * tracking params, keep everything else (posting id, locale, SPA hash routes are
 * <em>not</em> in scope here -- fragments are dropped but query order is kept).
 * SSRF / DNS checks happen at fetch time (Step 10).
 */
public final class UrlNormalizer {

    private static final int MAX_LEN = 4096;
    private static final Set<String> TRACKING = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name",
            "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "igshid", "mc_eid", "mc_cid", "_hsenc");

    private UrlNormalizer() {
    }

    public record Normalized(String original, String normalized, String urlHash) {
    }

    public static Normalized normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_URL, "url is required");
        }
        String raw = rawUrl.trim();
        if (raw.length() > MAX_LEN) {
            throw new ApiException(ErrorCode.INVALID_URL, "url exceeds " + MAX_LEN + " characters");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new ApiException(ErrorCode.INVALID_URL, "url is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ApiException(ErrorCode.INVALID_URL, "only http and https URLs are supported");
        }
        if (uri.getUserInfo() != null) {
            throw new ApiException(ErrorCode.INVALID_URL, "URLs with user info are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_URL, "url has no host");
        }
        host = host.toLowerCase();

        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);

        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();

        String query = uri.getRawQuery();
        String cleanedQuery = null;
        if (query != null && !query.isBlank()) {
            String kept = java.util.Arrays.stream(query.split("&"))
                    .filter(p -> {
                        String key = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
                        return !TRACKING.contains(key.toLowerCase());
                    })
                    .collect(Collectors.joining("&"));
            cleanedQuery = kept.isBlank() ? null : kept;
        }

        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        sb.append(path);
        if (cleanedQuery != null) {
            sb.append('?').append(cleanedQuery);
        }
        String normalized = sb.toString();
        return new Normalized(raw, normalized, sha256Hex(normalized));
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
