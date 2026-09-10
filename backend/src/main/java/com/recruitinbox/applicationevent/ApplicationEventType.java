package com.recruitinbox.applicationevent;

/**
 * {@code application_events.type varchar(32)} -- design v1.1 section 4.
 *
 * <p>Interviews are distinct types (not a {@code round} column), plus
 * {@code ORIENTATION}. {@code CUSTOM} requires a non-blank {@code custom_label};
 * {@code custom_label} may also override the display name of any other type.
 * The same type may be attached to an application multiple times.
 */
public enum ApplicationEventType {
    DOCUMENT_DEADLINE,
    NCS,
    CODING_TEST,
    AI_ASSESSMENT,
    INTERVIEW_1,
    INTERVIEW_2,
    FINAL_INTERVIEW,
    RESULT_ANNOUNCEMENT,
    ORIENTATION,
    CUSTOM
}
