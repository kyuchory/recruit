package com.recruitinbox.applicationevent.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.ScheduleKind;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates an unconfirmed event. A date-less placeholder ({@code scheduleKind =
 * UNKNOWN}) is allowed. The service validates the {@code scheduleKind} <-> date
 * fields matrix (mirrors the V1 CHECK block) before persisting.
 */
public record CreateEventRequest(
        @NotNull ApplicationEventType type,
        @Size(max = 200) String customLabel,
        Integer sortOrder,
        ScheduleKind scheduleKind,
        Instant scheduledAt,
        Instant startAt,
        Instant endAt,
        LocalDate scheduledDate,
        @Size(max = 64) String timezone,
        String location,
        @Size(max = 4096) String url,
        @Size(max = 10_000) String notes) {
}
