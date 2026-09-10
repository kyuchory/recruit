package com.recruitinbox.applicationevent;

import java.time.DateTimeException;
import java.time.ZoneId;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/**
 * Fails fast (422) on an inconsistent schedule before the row hits the DB,
 * mirroring the {@code application_events} CHECK block (v1.1 section 6.3) so the
 * caller gets a precise message instead of a generic constraint violation.
 */
final class ScheduleShapeValidator {

    private ScheduleShapeValidator() {
    }

    static void validate(ApplicationEvent e) {
        if (e.getType() == ApplicationEventType.CUSTOM
                && (e.getCustomLabel() == null || e.getCustomLabel().isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "customLabel is required when type = CUSTOM");
        }
        try {
            ZoneId.of(e.getTimezone());
        } catch (DateTimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "timezone is not a valid IANA zone id");
        }

        boolean hasDate = e.getScheduledDate() != null;
        boolean hasAt = e.getScheduledAt() != null;
        boolean hasStart = e.getStartAt() != null;
        boolean hasEnd = e.getEndAt() != null;

        switch (e.getScheduleKind()) {
            case EXACT -> {
                if (hasDate || !(hasAt || hasStart)) {
                    throw ambiguous("EXACT needs scheduledAt or startAt and no scheduledDate");
                }
            }
            case DATE_ONLY -> {
                if (!hasDate || hasAt || hasStart || hasEnd) {
                    throw ambiguous("DATE_ONLY needs scheduledDate only");
                }
            }
            case UNKNOWN, ROLLING, UNTIL_FILLED -> {
                if (hasDate || hasAt || hasStart || hasEnd) {
                    throw ambiguous(e.getScheduleKind() + " must carry no date fields");
                }
            }
            default -> throw ambiguous("unknown scheduleKind");
        }

        if ((e.getScheduleKind() == ScheduleKind.ROLLING || e.getScheduleKind() == ScheduleKind.UNTIL_FILLED)
                && e.getType() != ApplicationEventType.DOCUMENT_DEADLINE) {
            throw ambiguous("ROLLING / UNTIL_FILLED is only valid for DOCUMENT_DEADLINE");
        }
        if (hasAt && hasStart && !e.getScheduledAt().equals(e.getStartAt())) {
            throw ambiguous("scheduledAt must equal startAt when both are set");
        }
        if (hasEnd && !hasStart) {
            throw ambiguous("endAt requires startAt");
        }
        if (hasEnd && e.getEndAt().isBefore(e.getStartAt())) {
            throw ambiguous("endAt must not be before startAt");
        }
        if (e.getStatus() == EventStatus.SCHEDULED
                && e.getScheduleKind() != ScheduleKind.EXACT
                && e.getScheduleKind() != ScheduleKind.DATE_ONLY) {
            throw ambiguous("SCHEDULED requires scheduleKind EXACT or DATE_ONLY");
        }
    }

    private static ApiException ambiguous(String msg) {
        return new ApiException(ErrorCode.AMBIGUOUS_SCHEDULE, msg);
    }
}
