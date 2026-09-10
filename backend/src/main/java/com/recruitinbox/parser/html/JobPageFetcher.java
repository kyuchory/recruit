package com.recruitinbox.parser.html;

/** Fetches raw job-posting HTML under the SSRF + size + timeout policy. */
public interface JobPageFetcher {

    /**
     * @throws FetchException with a stable code:
     *         {@code UNSAFE_URL}, {@code FETCH_BLOCKED} (403/login/captcha),
     *         {@code FETCH_NOT_HTML}, {@code FETCH_TOO_LARGE},
     *         {@code FETCH_TIMEOUT}, {@code FETCH_ERROR}
     */
    FetchResult fetch(String url);

    record FetchResult(String finalUrl, String html, String contentType) {
    }

    class FetchException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public FetchException(String code, boolean retryable, String message) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() {
            return code;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
