package com.recruitinbox.applicationevent;

/**
 * {@code application_events.status varchar(16)} -- design v1.1 section 4.
 * {@code SCHEDULED} is only allowed with {@code schedule_kind IN ('EXACT','DATE_ONLY')}
 * (V1 CHECK). Confirmed + exact date => service sets {@code SCHEDULED}.
 */
public enum EventStatus {
    UNSCHEDULED,
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
