package com.recruitinbox.notification;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code notifications} (Flyway V1, v1.1 section 6.4) -- a scheduled send AND the
 * outbox row. {@code event_schedule_version}/{@code rule_version} are the
 * snapshot at creation; a mismatch at dispatch time cancels the send.
 * IN_APP uses {@code visible_at}/{@code read_at}; there is no external delivery.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "event_schedule_version", nullable = false)
    private long eventScheduleVersion;

    @Column(name = "rule_version", nullable = false)
    private long ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false, updatable = false)
    private NotificationChannel channel;

    @Column(name = "scheduled_send_at", nullable = false)
    private Instant scheduledSendAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "idempotency_key", length = 200, nullable = false)
    private String idempotencyKey;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "visible_at")
    private Instant visibleAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
