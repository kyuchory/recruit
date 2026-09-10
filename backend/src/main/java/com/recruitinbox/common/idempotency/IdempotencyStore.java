package com.recruitinbox.common.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/**
 * {@code api_idempotency} (composite PK, no version). Used inside the same
 * transaction as the business write (v1.1 section 7.1): reserve the key, do the
 * work, store the response, commit together. A rollback also drops the
 * reservation so the client may retry.
 */
@Component
public class IdempotencyStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotencyStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record Replay(int status, JsonNode body) {
    }

    /**
     * @return empty when this call now owns the key (proceed); a {@link Replay}
     *         when a completed response already exists for it.
     * @throws ApiException IDEMPOTENCY_CONFLICT on key reuse with a different
     *         request, or while an identical request is still in flight.
     */
    public Optional<Replay> reserveOrReplay(UUID ownerId, String key, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO api_idempotency (owner_id, key, request_hash, expires_at)
                VALUES (?, ?, ?, now() + interval '24 hours')
                ON CONFLICT (owner_id, key) DO NOTHING
                """, ownerId, key, requestHash);
        if (inserted == 1) {
            return Optional.empty();
        }
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT request_hash, response_status, response_json::text AS response_json
                FROM api_idempotency WHERE owner_id = ? AND key = ?
                """, ownerId, key);
        if (!requestHash.equals(row.get("request_hash"))) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was reused with a different request");
        }
        Object status = row.get("response_status");
        if (status == null) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "an identical request is already being processed");
        }
        try {
            JsonNode body = objectMapper.readTree((String) row.get("response_json"));
            return Optional.of(new Replay(((Number) status).intValue(), body));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL, "stored idempotent response is unreadable");
        }
    }

    public void complete(UUID ownerId, String key, int status, Object body) {
        try {
            jdbc.update("""
                    UPDATE api_idempotency
                    SET response_status = ?, response_json = ?::jsonb
                    WHERE owner_id = ? AND key = ?
                    """, status, objectMapper.writeValueAsString(body), ownerId, key);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL, "failed to persist idempotent response");
        }
    }
}
