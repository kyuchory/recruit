package com.recruitinbox.applicationevent;

/**
 * {@code application_events.result varchar(16)} -- design v1.1 section 4.
 * A result never implies a final decision and never auto-changes the
 * application status or other events (v1.1 section 4).
 */
public enum EventResult {
    NOT_STARTED,
    WAITING,
    PASSED,
    FAILED,
    SKIPPED
}
