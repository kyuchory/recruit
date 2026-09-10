package com.recruitinbox.parser;

import java.util.List;
import java.util.Map;

/** Result contract for an {@link ExtractionProcessor} run. */
public record ProcessOutcome(Kind kind, Map<String, Object> result, List<Object> warnings, String errorCode) {

    public enum Kind {
        /** Proposal produced; store {@link #result} and stop. */
        SUCCEEDED,
        /** Could not read the source; user must supply text/image. Not retried. */
        NEEDS_INPUT,
        /** Transient failure; re-queue with backoff up to the attempt cap. */
        RETRYABLE,
        /** Permanent failure (bad input, blocked, budget). Not retried. */
        FAILED
    }

    public static ProcessOutcome succeeded(Map<String, Object> result, List<Object> warnings) {
        return new ProcessOutcome(Kind.SUCCEEDED, result, warnings == null ? List.of() : warnings, null);
    }

    public static ProcessOutcome needsInput(String code, List<Object> warnings) {
        return new ProcessOutcome(Kind.NEEDS_INPUT, Map.of(), warnings == null ? List.of() : warnings, code);
    }

    public static ProcessOutcome retryable(String code) {
        return new ProcessOutcome(Kind.RETRYABLE, Map.of(), List.of(), code);
    }

    public static ProcessOutcome failed(String code) {
        return new ProcessOutcome(Kind.FAILED, Map.of(), List.of(), code);
    }
}
