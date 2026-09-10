package com.recruitinbox.notification.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.recruitinbox.notification.NotificationAnchor;
import com.recruitinbox.notification.NotificationChannel;
import com.recruitinbox.notification.NotificationMode;
import com.recruitinbox.notification.NotificationRule;

import jakarta.validation.constraints.NotNull;

public final class RuleDtos {

    private RuleDtos() {
    }

    public record CreateRuleRequest(
            @NotNull NotificationChannel channel,
            @NotNull NotificationAnchor anchor,
            @NotNull NotificationMode mode,
            Integer offsetMinutes,
            Integer offsetDays,
            LocalTime localTime,
            Boolean enabled) {
    }

    public record UpdateRuleRequest(
            @NotNull Long expectedVersion,
            NotificationChannel channel,
            NotificationAnchor anchor,
            NotificationMode mode,
            Integer offsetMinutes,
            Integer offsetDays,
            LocalTime localTime,
            Boolean enabled) {
    }

    public record RuleResponse(
            UUID id,
            UUID eventId,
            NotificationChannel channel,
            NotificationAnchor anchor,
            NotificationMode mode,
            Integer offsetMinutes,
            Integer offsetDays,
            LocalTime localTime,
            boolean enabled,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public static RuleResponse from(NotificationRule r) {
            return new RuleResponse(r.getId(), r.getEventId(), r.getChannel(), r.getAnchor(), r.getMode(),
                    r.getOffsetMinutes(), r.getOffsetDays(), r.getLocalTime(), r.isEnabled(),
                    r.getVersion() == null ? 0L : r.getVersion(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
