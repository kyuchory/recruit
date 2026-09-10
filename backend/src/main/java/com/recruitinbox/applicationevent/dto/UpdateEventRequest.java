package com.recruitinbox.applicationevent.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.EventResult;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH. {@code null} = leave unchanged. Changing any schedule-affecting field
 * (type / customLabel / scheduleKind / scheduledAt / startAt / endAt /
 * scheduledDate / timezone) resets confirmation and bumps {@code scheduleVersion};
 * changing only status / result / notes / sortOrder / location / url does not.
 */
public record UpdateEventRequest(
        @NotNull Long expectedVersion,
        ApplicationEventType type,
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
        @Size(max = 10_000) String notes,
        EventStatus status,
        EventResult result,
        /** true clears every date field and moves the event to UNKNOWN. */
        Boolean clearSchedule) {
}
