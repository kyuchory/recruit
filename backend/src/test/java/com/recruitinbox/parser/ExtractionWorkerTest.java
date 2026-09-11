package com.recruitinbox.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

/**
 * Not {@code @Transactional}: the claim uses its own committed transactions, so
 * the test data must be really committed and cleaned up afterwards.
 */
@SpringBootTest(properties = "parser.enabled=false") // drive the worker manually, no background poller races
class ExtractionWorkerTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    ExtractionRunClaimDao claimDao;
    @Autowired
    ExtractionRunRepository runs;
    @Autowired
    ExtractionWorker worker;
    @Autowired
    LinkRepository links;
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;

    private UUID owner;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("worker+" + UUID.randomUUID() + "@example.com");
        owner = users.save(u).getId();
    }

    private UUID newLink() {
        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        return links.save(l).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM extraction_runs WHERE owner_id = ?", owner);
        jdbc.update("DELETE FROM applications WHERE owner_id = ?", owner);
        jdbc.update("DELETE FROM links WHERE owner_id = ?", owner);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
    }

    private ExtractionRun queued(long generation) {
        ExtractionRun r = new ExtractionRun();
        r.setOwnerId(owner);
        r.setLinkId(newLink());
        r.setGeneration(generation);
        r.setSourceKind("url");
        r.setStatus(ExtractionRunStatus.QUEUED);
        r.setSchemaVersion(ParserVersions.SCHEMA);
        r.setParserVersion(ParserVersions.PARSER);
        r.setModelConfigVersion(ParserVersions.MODEL_CONFIG);
        r.setNextAttemptAt(Instant.now().minusSeconds(1));
        return runs.save(r);
    }

    @Test
    void eachDueRunIsClaimedExactlyOnce() {
        UUID a = queued(1).getId();
        UUID b = queued(2).getId();

        List<UUID> first = claimDao.claimDue(10, 150);
        assertThat(first).containsExactlyInAnyOrder(a, b);
        assertThat(runs.findById(a).orElseThrow().getStatus()).isEqualTo(ExtractionRunStatus.RUNNING);

        List<UUID> second = claimDao.claimDue(10, 150);
        assertThat(second).isEmpty();
    }

    @Test
    void expiredLeaseIsRequeuedBelowCapAndFailedAtCap() {
        ExtractionRun below = queued(1);
        below.setStatus(ExtractionRunStatus.RUNNING);
        below.setLeaseToken(UUID.randomUUID());
        below.setLeaseUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        below.setAttemptCount(1);
        runs.save(below);

        ExtractionRun atCap = queued(2);
        atCap.setStatus(ExtractionRunStatus.RUNNING);
        atCap.setLeaseToken(UUID.randomUUID());
        atCap.setLeaseUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        atCap.setAttemptCount(3);
        runs.save(atCap);

        int recovered = claimDao.recoverExpiredLeases(3, 15);
        assertThat(recovered).isEqualTo(2);
        assertThat(runs.findById(below.getId()).orElseThrow().getStatus())
                .isEqualTo(ExtractionRunStatus.QUEUED);
        ExtractionRun failed = runs.findById(atCap.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ExtractionRunStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("LEASE_EXPIRED");
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ExtractionProcessor processor;

    @Test
    void processClaimedFinalizesTheRunFromTheProcessorOutcome() {
        org.mockito.Mockito.when(processor.process(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ProcessOutcome.succeeded(
                        java.util.Map.of("schemaVersion", "job.v1.1"), java.util.List.of("OK")));

        UUID id = queued(1).getId();
        claimDao.claimDue(10, 150);

        worker.processClaimed(id);

        ExtractionRun done = runs.findById(id).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ExtractionRunStatus.SUCCEEDED);
        assertThat(done.getResult()).containsEntry("schemaVersion", "job.v1.1");
        assertThat(done.getFinishedAt()).isNotNull();
        assertThat(done.getLeaseToken()).isNull();
    }

    @Test
    void finalizeIsFencedWhenTheLeaseChanged() {
        UUID id = queued(1).getId();
        claimDao.claimDue(10, 150);
        UUID staleLease = runs.findById(id).orElseThrow().getLeaseToken();

        // another executor takes over
        jdbc.update("UPDATE extraction_runs SET lease_token = gen_random_uuid() WHERE id = ?", id);

        boolean applied = claimDao.finishSucceeded(id, staleLease, java.util.Map.of("x", 1), List.of(), null);
        assertThat(applied).isFalse();
        assertThat(runs.findById(id).orElseThrow().getStatus()).isEqualTo(ExtractionRunStatus.RUNNING);
    }

    @Test
    void retryableSchedulesRetryThenFailsAtCap() {
        ExtractionRun r = queued(1);
        r.setStatus(ExtractionRunStatus.RUNNING);
        UUID lease = UUID.randomUUID();
        r.setLeaseToken(lease);
        r.setLeaseUntil(Instant.now().plusSeconds(120));
        r.setAttemptCount(1);
        runs.save(r);

        assertThat(claimDao.scheduleRetryOrFail(r.getId(), lease, 3, 5, "TRANSIENT")).isTrue();
        assertThat(runs.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(ExtractionRunStatus.QUEUED);

        // simulate the attempt cap being reached
        jdbc.update("UPDATE extraction_runs SET status='RUNNING', attempt_count=3, lease_token=?, "
                + "lease_until=now()+interval '2 minutes' WHERE id=?", lease, r.getId());
        assertThat(claimDao.scheduleRetryOrFail(r.getId(), lease, 3, 5, "TRANSIENT")).isTrue();
        assertThat(runs.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(ExtractionRunStatus.FAILED);
    }
}
