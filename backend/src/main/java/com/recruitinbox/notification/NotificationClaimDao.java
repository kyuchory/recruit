package com.recruitinbox.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Native queue operations for notification outbox rows. Claims are committed
 * before provider calls and every completion is fenced by the lease token.
 */
@Repository
public class NotificationClaimDao {

    public record Claim(UUID id, UUID leaseToken) {
    }

    private final JdbcTemplate jdbc;

    public NotificationClaimDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Claim> claimDue(int limit, int leaseSeconds) {
        return jdbc.query("""
                WITH picked AS (
                  SELECT id FROM notifications
                  WHERE status = 'PENDING'
                    AND scheduled_send_at <= now()
                    AND next_attempt_at <= now()
                  ORDER BY scheduled_send_at, id
                  FOR UPDATE SKIP LOCKED
                  LIMIT ?
                )
                UPDATE notifications n
                SET status = 'DISPATCHING',
                    lease_token = gen_random_uuid(),
                    lease_until = now() + make_interval(secs => ?),
                    version = n.version + 1,
                    updated_at = now()
                FROM picked
                WHERE n.id = picked.id
                RETURNING n.id, n.lease_token
                """, (rs, row) -> new Claim(
                        rs.getObject("id", UUID.class), rs.getObject("lease_token", UUID.class)),
                limit, leaseSeconds);
    }

    public int recoverExpiredLeases(int backoffSeconds) {
        return jdbc.update("""
                UPDATE notifications
                SET status = CASE WHEN expires_at <= now() THEN 'CANCELLED' ELSE 'PENDING' END,
                    next_attempt_at = now() + make_interval(secs => ?),
                    completed_at = CASE WHEN expires_at <= now() THEN now() ELSE completed_at END,
                    lease_token = NULL,
                    lease_until = NULL,
                    version = version + 1,
                    updated_at = now()
                WHERE status = 'DISPATCHING' AND lease_until < now()
                """, backoffSeconds);
    }

    public boolean finishInApp(UUID id, UUID leaseToken, Instant visibleAt) {
        return jdbc.update("""
                UPDATE notifications
                SET status = 'COMPLETED', visible_at = ?, completed_at = ?,
                    lease_token = NULL, lease_until = NULL,
                    version = version + 1, updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'DISPATCHING'
                """, Timestamp.from(visibleAt), Timestamp.from(visibleAt), id, leaseToken) == 1;
    }

    public boolean finishCompleted(UUID id, UUID leaseToken, Instant completedAt) {
        return finishTerminal(id, leaseToken, NotificationStatus.COMPLETED, completedAt);
    }

    public boolean finishFailed(UUID id, UUID leaseToken, Instant completedAt) {
        return finishTerminal(id, leaseToken, NotificationStatus.FAILED, completedAt);
    }

    public boolean finishCancelled(UUID id, UUID leaseToken, Instant completedAt) {
        return finishTerminal(id, leaseToken, NotificationStatus.CANCELLED, completedAt);
    }

    public boolean releaseForRetry(UUID id, UUID leaseToken, int backoffSeconds) {
        return jdbc.update("""
                UPDATE notifications
                SET status = CASE WHEN expires_at <= now() THEN 'CANCELLED' ELSE 'PENDING' END,
                    next_attempt_at = now() + make_interval(secs => ?),
                    completed_at = CASE WHEN expires_at <= now() THEN now() ELSE completed_at END,
                    lease_token = NULL,
                    lease_until = NULL,
                    version = version + 1,
                    updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'DISPATCHING'
                """, backoffSeconds, id, leaseToken) == 1;
    }

    private boolean finishTerminal(UUID id, UUID leaseToken, NotificationStatus status, Instant completedAt) {
        return jdbc.update("""
                UPDATE notifications
                SET status = ?, completed_at = ?,
                    lease_token = NULL, lease_until = NULL,
                    version = version + 1, updated_at = now()
                WHERE id = ? AND lease_token = ? AND status = 'DISPATCHING'
                """, status.name(), Timestamp.from(completedAt), id, leaseToken) == 1;
    }
}
