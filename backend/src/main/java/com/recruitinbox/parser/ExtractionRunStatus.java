package com.recruitinbox.parser;

/** {@code extraction_runs.status varchar(16)} -- design v1.1 section 4. */
public enum ExtractionRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    NEEDS_INPUT,
    FAILED,
    CANCELLED
}
