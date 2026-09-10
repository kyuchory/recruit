package com.recruitinbox.common.error;

import java.util.Map;

/** Business error carrying an {@link ErrorCode} and optional per-field detail. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, String> fields;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, String> fields) {
        super(message);
        this.code = code;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException versionConflict() {
        return new ApiException(ErrorCode.VERSION_CONFLICT,
                "resource was modified by another request; reload and retry");
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
