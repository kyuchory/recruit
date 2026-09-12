package com.recruitinbox.applicationevent.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.EventResult;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;

public record EventResponse(
        UUID id,
        UUID applicationId,
        ApplicationEventType type,
        String customLabel,
        int sortOrder,
        ScheduleKind scheduleKind,
        Instant scheduledAt,
        Instant startAt,
        Instant endAt,
        LocalDate scheduledDate,
        String timezone,
        Integer daysUntil,
        String location,
        String url,
        String notes,
        EventStatus status,
        EventResult result,
        Instant confirmedAt,
        long scheduleVersion,
        Map<String, Object> fieldMeta,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static EventResponse from(ApplicationEvent e) {
        return new EventResponse(
                e.getId(), e.getApplicationId(), e.getType(), e.getCustomLabel(), e.getSortOrder(),
                e.getScheduleKind(), e.getScheduledAt(), e.getStartAt(), e.getEndAt(), e.getScheduledDate(),
                e.getTimezone(), daysUntil(e), e.getLocation(), e.getUrl(), e.getNotes(),
                e.getStatus(), e.getResult(), e.getConfirmedAt(), e.getScheduleVersion(),
                e.getFieldMeta(),
                e.getVersion() == null ? 0L : e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static Integer daysUntil(ApplicationEvent event) {
        ZoneId zone = ZoneId.of(event.getTimezone());
        LocalDate target = event.getScheduledDate();
        if (target == null) {
            Instant instant = event.getScheduledAt() != null ? event.getScheduledAt()
                    : event.getStartAt() != null ? event.getStartAt() : event.getEndAt();
            if (instant == null) return null;
            target = instant.atZone(zone).toLocalDate();
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(zone), target));
    }
}
