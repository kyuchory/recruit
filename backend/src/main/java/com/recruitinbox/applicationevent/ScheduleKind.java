package com.recruitinbox.applicationevent;

/**
 * {@code application_events.schedule_kind varchar(16)} -- design v1.1 section 4 / 6.3.
 *
 * <ul>
 *   <li>{@code EXACT}      -- has {@code scheduled_at} and/or {@code start_at} (no {@code scheduled_date})</li>
 *   <li>{@code DATE_ONLY}  -- has {@code scheduled_date} only</li>
 *   <li>{@code UNKNOWN}    -- no date fields (placeholder)</li>
 *   <li>{@code ROLLING} / {@code UNTIL_FILLED} -- no date fields, {@code DOCUMENT_DEADLINE} only</li>
 * </ul>
 */
public enum ScheduleKind {
    EXACT,
    DATE_ONLY,
    UNKNOWN,
    ROLLING,
    UNTIL_FILLED
}
