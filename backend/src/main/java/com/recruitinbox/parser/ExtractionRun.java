package com.recruitinbox.parser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * {@code extraction_runs} (Flyway V1, design v1.1 section 6.2) -- one immutable
 * analysis attempt for a link generation. It is the durable source of truth for
 * async URL analysis: the row is committed <em>before</em> the 202 response, and
 * the Step 9 poller claims it via {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p>Results live only in {@link #result}; the parser never writes back onto
 * {@code applications} / {@code application_events} (v1.1 section 6.6).
 */
@Entity
@Table(name = "extraction_runs")
@Getter
@Setter
public class ExtractionRun extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    @Column(name = "generation", nullable = false, updatable = false)
    private long generation;

    @Column(name = "source_kind", length = 24, nullable = false)
    private String sourceKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ExtractionRunStatus status = ExtractionRunStatus.QUEUED;

    @Column(name = "progress_stage", length = 32)
    private String progressStage;

    @Column(name = "schema_version", length = 32, nullable = false)
    private String schemaVersion;

    @Column(name = "parser_version", length = 64, nullable = false)
    private String parserVersion;

    @Column(name = "model_config_version", length = 64, nullable = false)
    private String modelConfigVersion;

    @Column(name = "model_id", columnDefinition = "text")
    private String modelId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> result = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", columnDefinition = "jsonb", nullable = false)
    private List<Object> warnings = new ArrayList<>();

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
