package com.recruitinbox.auth;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an internal {@code users.id} from an external identity
 * ({@code auth_identities.provider + provider_subject}). Never merges accounts by
 * email string alone (v1.1 section 6.2).
 */
@Service
public class UserIdentityService {

    private final JdbcTemplate jdbc;

    public UserIdentityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID upsertFromOidc(String provider, String subject, String email) {
        List<UUID> found = jdbc.query(
                "SELECT owner_id FROM auth_identities WHERE provider = ? AND provider_subject = ?",
                (rs, n) -> rs.getObject("owner_id", UUID.class), provider, subject);
        if (!found.isEmpty()) {
            return found.get(0);
        }

        UUID userId = jdbc.queryForObject("""
                INSERT INTO users (email, email_verified_at, email_enabled, state, version)
                VALUES (?, now(), false, 'ACTIVE', 1)
                RETURNING id
                """, UUID.class, email);
        jdbc.update("""
                INSERT INTO auth_identities (owner_id, provider, provider_subject, version)
                VALUES (?, ?, ?, 1)
                """, userId, provider, subject);
        return userId;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> me(UUID userId) {
        return jdbc.queryForMap("""
                SELECT id, email, timezone, email_enabled, state
                FROM users WHERE id = ?
                """, userId);
    }
}
