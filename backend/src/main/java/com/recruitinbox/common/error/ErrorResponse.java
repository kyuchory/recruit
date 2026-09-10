package com.recruitinbox.common.error;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error envelope:
 * <pre>{ "error": { "code", "message", "retryable", "requestId", "fields": {} } }</pre>
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(
            String code,
            String message,
            boolean retryable,
            String requestId,
            Map<String, String> fields) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId, Map<String, String> fields) {
        return new ErrorResponse(new Body(
                code.name(),
                message,
                code.retryable(),
                requestId,
                fields == null || fields.isEmpty() ? Map.of() : fields));
    }
}
