package com.recruitinbox.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the real PostgreSQL + Redis stack.
 *
 * <p>Spins up singleton Testcontainers (PostgreSQL 18, Redis 8) once per JVM and
 * points Spring at them via {@link DynamicPropertySource}. This removes the old
 * requirement to run {@code docker compose up} before {@code ./gradlew test} --
 * a working Docker daemon is the only prerequisite.
 *
 * <p>Subclasses keep their own {@code @SpringBootTest} / {@code @DataJpaTest} /
 * {@code @AutoConfigureMockMvc} annotations; this class only supplies the
 * connection properties.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"))
                .withDatabaseName("recruit_inbox")
                .withUsername("recruit")
                .withPassword("recruit_local_pw")
                .withReuse(true);
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datastoreProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
