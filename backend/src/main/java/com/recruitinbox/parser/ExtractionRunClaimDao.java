package com.recruitinbox.parser;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

/**
 * Native queue operations for {@link ExtractionRun} (v1.1 section 5.2):
 * short-transaction claim with {@code FOR UPDATE SKIP LOCKED}, lease recovery,
 * and lease-fenced finalization. Never held across a network call.
 */
@Repository
public class ExtractionRunClaimDao {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ExtractionRunClaimDao(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Claim up to {@code limit} due QUEUED runs -> RUNNING + fresh lease; returns claimed ids. */
    public List<UUID> claimDue(int limit, int leaseSeconds) {
        return jdbc.query("""
                WITH picked AS (
                  SELECT id FROM extraction_runs
                  WHERE status = 'QUEUED' AND next_attempt_at <= now()
                  ORDER BY next_attempt_at, id
                  FOR UPDATE SKIP LOCKED
                  LIMIT ?
                )
                UPDATE extraction_runs r
                SET status = 'RUNNING',
                    attempt_count = r.attempt_count + 1,
                    lease_token = gen_random_uuid(),
                    lease_until = now() + make_interval(secs => ?),
                    started_at = COALESCE(r.started_at, now()),
                    updated_at = now()
                FROM picked
                WHERE r.id = picked.id
                RETURNING r.id
                """, (rs, n) -> rs.getObject("id", UUID.class), limit, leaseSeconds);
    }

    /** Expired RUNNING leases: re-queue with backoff, or FAIL once the attempt cap is hit. */
    public int recoverExpiredLeases(int maxAttempts, int backoffSeconds) {
        return jdbc.update("""
                UPDATE extraction_runs
                SET status = CASE WHEN attempt_count >= ? THEN 'FAILED' ELSE 'QUEUED' END,
                    error_code = CASE WHEN attempt_count >= ? THEN 'LEASE_EXPIRED' ELSE error_code END,
                    finished_at = CASE WHEN attempt_count >= ? THEN now() ELSE finished_at END,
                    lease_token = NULL,
                    lease_until = NULL,
                    next_attempt_at = now() + make_interval(secs => ?),
                    updated_at = now()
                WHERE status = 'RUNNING' AND lease_until < now()
                """, maxAttempts, maxAttempts, maxAttempts, backoffSeconds);
    }

    public boolean finishSucceeded(UUID id, UUID leaseToken, Object result, Object warnings, String progressStage) {
        return jdbc.update("""
                UPDATE extraction_runs
                SET status = 'SUCCEEDED', result = ?::jsonb, warnings = ?::jsonb,
                    progress_stage = ?, error_code = NULL, finished_at = now(),
                    lease_token = NULL, lease_until = NULL, updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'RUNNING'
                """, json(result), json(warnings), progressStage, id, leaseToken) == 1;
    }

    public boolean finishTerminal(UUID id, UUID leaseToken, ExtractionRunStatus status,
            Object warnings, String errorCode) {
        return jdbc.update("""
                UPDATE extraction_runs
                SET status = ?, warnings = ?::jsonb, error_code = ?, finished_at = now(),
                    lease_token = NULL, lease_until = NULL, updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'RUNNING'
                """, status.name(), json(warnings), errorCode, id, leaseToken) == 1;
    }

    /** Retryable failure: back to QUEUED with backoff, or FAIL at the attempt cap. */
    public boolean scheduleRetryOrFail(UUID id, UUID leaseToken, int maxAttempts, int backoffSeconds, String errorCode) {
        return jdbc.update("""
                UPDATE extraction_runs
                SET status = CASE WHEN attempt_count >= ? THEN 'FAILED' ELSE 'QUEUED' END,
                    error_code = ?,
                    finished_at = CASE WHEN attempt_count >= ? THEN now() ELSE finished_at END,
                    next_attempt_at = now() + make_interval(secs => ?),
                    lease_token = NULL, lease_until = NULL, updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'RUNNING'
                """, maxAttempts, errorCode, maxAttempts, backoffSeconds, id, leaseToken) == 1;
    }

    private String json(Object o) {
        return objectMapper.writeValueAsString(o == null ? List.of() : o);
    }
}
