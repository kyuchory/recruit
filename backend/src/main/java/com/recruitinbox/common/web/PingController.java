package com.recruitinbox.common.web;

import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal connectivity probe used to verify the local stack end to end
 * (frontend -> backend -> PostgreSQL / Redis) before real features exist.
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public PingController(DataSource dataSource, RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "recruit-inbox-backend",
                "time", Instant.now().toString());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "postgres", checkPostgres(),
                "redis", checkRedis(),
                "flywayMigrations", countMigrations());
    }

    private String checkPostgres() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(one) ? "up" : "unexpected";
        } catch (RuntimeException ex) {
            return "down: " + ex.getMessage();
        }
    }

    private String checkRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? "up" : "unexpected";
        } catch (RuntimeException ex) {
            return "down: " + ex.getMessage();
        }
    }

    private Object countMigrations() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        } catch (RuntimeException ex) {
            return "unavailable";
        }
    }
}
