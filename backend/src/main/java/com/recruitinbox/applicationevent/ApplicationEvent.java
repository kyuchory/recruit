package com.recruitinbox.applicationevent;

import java.time.Instant;
import java.time.LocalDate;
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
 * {@code application_events} (Flyway V1, design v1.1 section 6.3) -- an
 * independent step/schedule of an application (서류 / NCS / 코딩테스트 /
 * AI 역량검사 / 면접 1·2·최종 / 발표 / 오리엔테이션 / 기타). Notifications are
 * managed per event.
 *
 * <p>Deliberately kept in the {@code applicationevent} package (v1.1 section 4)
 * so it is not confused with a Spring {@code ApplicationEvent}.
 *
 * <p>{@code owner_id} and {@code application_id} are plain {@code UUID}s; no
 * {@code @ManyToOne} to {@code Application}, and no {@code @OneToMany} back from
 * it -- the collection would need its own owner-scoped loading anyway.
 *
 * <p>Not re-expressed in JPA (DB CHECK block, v1.1 section 6.3):
 * CUSTOM ⇒ non-blank {@code custom_label}; {@code confirmed_by = owner_id};
 * {@code (confirmed_at IS NULL) = (confirmed_by IS NULL)};
 * {@code scheduled_at = start_at} when both set; {@code end_at >= start_at};
 * the {@code schedule_kind} ↔ date-fields matrix;
 * ROLLING/UNTIL_FILLED ⇒ DOCUMENT_DEADLINE; SCHEDULED ⇒ EXACT/DATE_ONLY.
 */
@Entity
@Table(name = "application_events")
@Getter
@Setter
public class ApplicationEvent extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private ApplicationEventType type;

    /** Display-name override for any type; required when {@code type = CUSTOM}. */
    @Column(name = "custom_label", length = 200)
    private String customLabel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_kind", length = 16, nullable = false)
    private ScheduleKind scheduleKind = ScheduleKind.UNKNOWN;

    /** Single instant (deadline / announcement). */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Start of an exam / interview window. */
    @Column(name = "start_at")
    private Instant startAt;

    /** End of the window; end-anchored reminders bind here. */
    @Column(name = "end_at")
    private Instant endAt;

    /** Local calendar date for {@code DATE_ONLY}; not a real deadline time. */
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone = "Asia/Seoul";

    @Column(name = "location", columnDefinition = "text")
    private String location;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "notes", columnDefinition = "text", nullable = false)
    private String notes = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 16, nullable = false)
    private EventResult result = EventResult.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EventStatus status = EventStatus.UNSCHEDULED;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Server-set to {@code owner_id} on confirm; never from the request. */
    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    /** Notification-validity version; bumped by the confirm/schedule-change path. */
    @Column(name = "schedule_version", nullable = false)
    private long scheduleVersion = 1L;

    /** {@code jsonb} per-field provenance -- see {@link com.recruitinbox.application.Application#getFieldMeta()}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_meta", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fieldMeta = new HashMap<>();
}
