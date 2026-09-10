package com.recruitinbox.notification;

import java.util.Map;

/**
 * Outbound email channel. {@code send} returns whether the provider accepted the
 * message -- acceptance is not the same as delivery, and an accepted message
 * cannot be recalled. Provider adapters (SES/Postmark/...) implement this.
 */
public interface EmailSender {

    Result send(String toAddress, String subject, String body, Map<String, Object> context);

    enum Outcome { ACCEPTED, RETRYABLE, FAILED }

    record Result(Outcome outcome, String providerMessageId, String errorCode) {
        public static Result accepted(String id) {
            return new Result(Outcome.ACCEPTED, id, null);
        }

        public static Result retryable(String code) {
            return new Result(Outcome.RETRYABLE, null, code);
        }

        public static Result failed(String code) {
            return new Result(Outcome.FAILED, null, code);
        }
    }
}
