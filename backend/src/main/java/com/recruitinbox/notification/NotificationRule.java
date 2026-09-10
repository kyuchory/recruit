package com.recruitinbox.notification;

import java.time.LocalTime;
import java.util.UUID;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** {@code notification_rules} (Flyway V1, v1.1 section 6.4). One channel + one offset. */
@Entity
@Table(name = "notification_rules")
@Getter
@Setter
public class NotificationRule extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor", length = 16, nullable = false)
    private NotificationAnchor anchor;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 24, nullable = false)
    private NotificationMode mode;

    @Column(name = "offset_minutes")
    private Integer offsetMinutes;

    @Column(name = "offset_days")
    private Integer offsetDays;

    @Column(name = "local_time")
    private LocalTime localTime;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
