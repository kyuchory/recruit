package com.recruitinbox.common.security;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds a fixed dev user so {@code curl} works out of the box under {@code !prod}.
 * Native upsert because {@code users.id} is JPA-generated and we want a stable id.
 */
@Component
@Profile("!prod")
public class DevUserInitializer implements CommandLineRunner {

    public static final UUID DEV_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;

    public DevUserInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.update("""
                INSERT INTO users (id, email, timezone, email_enabled, state, version)
                VALUES (?, ?, 'Asia/Seoul', false, 'ACTIVE', 1)
                ON CONFLICT (id) DO NOTHING
                """, DEV_USER_ID, "dev@recruit-inbox.local");
    }
}
