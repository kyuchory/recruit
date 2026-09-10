package com.recruitinbox.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;

/**
 * Materializes {@code notifications} from an event's rules (v1.1 section 9).
 *
 * <p>Full re-plan on every call: cancel this event's still-PENDING notifications,
 * then re-create the ones that are still valid and in the future. Eligibility =
 * confirmed + SCHEDULED + EXACT/DATE_ONLY. Send times in the past are skipped
 * (no "fire a day-before reminder now"). Only EMAIL + IN_APP are dispatched in
 * the MVP; other channels are accepted as rules but never materialized.
 */
@Service
public class NotificationPlanner {

    private final ApplicationEventRepository events;
    private final NotificationRuleRepository rules;
    private final NotificationRepository notifications;

    public NotificationPlanner(ApplicationEventRepository events, NotificationRuleRepository rules,
            NotificationRepository notifications) {
        this.events = events;
        this.rules = rules;
        this.notifications = notifications;
    }

    @Transactional
    public void cancelAllForEvent(UUID eventId) {
        for (Notification n : notifications.findByEventIdAndStatus(eventId, NotificationStatus.PENDING)) {
            n.setStatus(NotificationStatus.CANCELLED);
            notifications.save(n);
        }
    }

    @Transactional
    public void replan(UUID ownerId, UUID eventId) {
        ApplicationEvent e = events.findByIdAndOwnerId(eventId, ownerId).orElse(null);
        if (e == null) {
            return;
        }
        for (Notification n : notifications.findByEventIdAndStatus(eventId, NotificationStatus.PENDING)) {
            n.setStatus(NotificationStatus.CANCELLED);
            notifications.save(n);
        }

        boolean eligible = e.getConfirmedAt() != null
                && e.getStatus() == EventStatus.SCHEDULED
                && (e.getScheduleKind() == ScheduleKind.EXACT || e.getScheduleKind() == ScheduleKind.DATE_ONLY);
        if (!eligible) {
            return;
        }

        Instant now = Instant.now();
        List<NotificationRule> active = rules.findByEventIdAndOwnerIdAndEnabledTrue(eventId, ownerId);
        for (NotificationRule r : active) {
            if (r.getChannel() != NotificationChannel.EMAIL && r.getChannel() != NotificationChannel.IN_APP) {
                continue;
            }
            Instant sendAt = computeSendAt(e, r);
            if (sendAt == null || !sendAt.isAfter(now)) {
                continue;
            }
            long ruleVersion = r.getVersion() == null ? 0L : r.getVersion();
            String key = "rule:" + r.getId() + ":esv:" + e.getScheduleVersion() + ":rv:" + ruleVersion;
            if (notifications.findByIdempotencyKey(key).isPresent()) {
                continue;
            }
            Notification n = new Notification();
            n.setOwnerId(ownerId);
            n.setEventId(eventId);
            n.setRuleId(r.getId());
            n.setEventScheduleVersion(e.getScheduleVersion());
            n.setRuleVersion(ruleVersion);
            n.setChannel(r.getChannel());
            n.setScheduledSendAt(sendAt);
            n.setExpiresAt(computeExpiresAt(e, r, sendAt));
            n.setIdempotencyKey(key);
            n.setPayload(payload(e));
            n.setNextAttemptAt(sendAt);
            n.setStatus(NotificationStatus.PENDING);
            notifications.save(n);
        }
    }

    private Instant computeSendAt(ApplicationEvent e, NotificationRule r) {
        if (e.getScheduleKind() == ScheduleKind.EXACT && r.getMode() == NotificationMode.BEFORE_MINUTES) {
            Instant anchor = anchorInstant(e, r.getAnchor());
            if (anchor == null || r.getOffsetMinutes() == null) {
                return null;
            }
            return anchor.minus(r.getOffsetMinutes(), ChronoUnit.MINUTES);
        }
        if (e.getScheduleKind() == ScheduleKind.DATE_ONLY && r.getMode() == NotificationMode.CALENDAR_DAYS) {
            if (e.getScheduledDate() == null || r.getOffsetDays() == null || r.getLocalTime() == null) {
                return null;
            }
            LocalDate day = e.getScheduledDate().minusDays(r.getOffsetDays());
            return day.atTime(r.getLocalTime()).atZone(ZoneId.of(e.getTimezone())).toInstant();
        }
        return null;
    }

    private Instant computeExpiresAt(ApplicationEvent e, NotificationRule r, Instant sendAt) {
        if (e.getScheduleKind() == ScheduleKind.EXACT) {
            Instant anchor = anchorInstant(e, r.getAnchor());
            if (anchor == null) {
                return sendAt.plus(30, ChronoUnit.MINUTES);
            }
            return Integer.valueOf(0).equals(r.getOffsetMinutes())
                    ? anchor.plus(5, ChronoUnit.MINUTES) : anchor;
        }
        LocalDate boundary = e.getScheduledDate().plusDays(1);
        return boundary.atStartOfDay(ZoneId.of(e.getTimezone())).toInstant();
    }

    private Instant anchorInstant(ApplicationEvent e, NotificationAnchor anchor) {
        return switch (anchor) {
            case SCHEDULED_AT -> e.getScheduledAt();
            case START_AT -> e.getStartAt();
            case END_AT -> e.getEndAt();
            case DATE -> null;
        };
    }

    private Map<String, Object> payload(ApplicationEvent e) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventType", e.getType().name());
        p.put("customLabel", e.getCustomLabel());
        p.put("scheduledAt", e.getScheduledAt() == null ? null : e.getScheduledAt().toString());
        p.put("scheduledDate", e.getScheduledDate() == null ? null : e.getScheduledDate().toString());
        p.put("path", "/app/applications/" + e.getApplicationId());
        return p;
    }
}
