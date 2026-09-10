package com.recruitinbox.common.error;

import org.springframework.http.HttpStatus;

/** Stable machine codes returned in {@code error.code}. */
public enum ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, false),
    FORBIDDEN(HttpStatus.FORBIDDEN, false),
    NOT_FOUND(HttpStatus.NOT_FOUND, false),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, false),
    INVALID_PAYLOAD(HttpStatus.BAD_REQUEST, false),
    INVALID_URL(HttpStatus.BAD_REQUEST, false),
    INVALID_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, false),
    UNSUPPORTED_MEDIA(HttpStatus.UNSUPPORTED_MEDIA_TYPE, false),
    ASSET_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, false),
    UNSAFE_URL(HttpStatus.UNPROCESSABLE_CONTENT, false),
    AMBIGUOUS_SCHEDULE(HttpStatus.UNPROCESSABLE_CONTENT, false),
    CHANNEL_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, false),
    VERSION_CONFLICT(HttpStatus.CONFLICT, false),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, false),
    DUPLICATE(HttpStatus.CONFLICT, false),
    RUN_IN_PROGRESS(HttpStatus.CONFLICT, false),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, false),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, false),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, true),
    FEATURE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, true),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, false);

    private final HttpStatus status;
    private final boolean retryable;

    ErrorCode(HttpStatus status, boolean retryable) {
        this.status = status;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}
