package com.recruitinbox.parser;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed extraction queue worker (v1.1 section 5.2; no RabbitMQ):
 * <ul>
 *   <li>{@link #poll()} claims due QUEUED runs in a short tx
 *       ({@code FOR UPDATE SKIP LOCKED}) and hands each to the bounded parser
 *       pool; work survives restarts because the row stays in the DB.</li>
 *   <li>{@link #recoverLeases()} re-queues runs whose lease expired, failing
 *       them once the attempt cap is reached.</li>
 *   <li>Finalization is lease-fenced: a stale worker whose lease was revoked
 *       cannot overwrite the result.</li>
 * </ul>
 * {@code parser.enabled=false} is the kill switch (URL save + manual entry keep working).
 */
@Component
public class ExtractionWorker {

    private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);

    private final ExtractionRunClaimDao claimDao;
    private final ExtractionRunRepository runs;
    private final ExtractionProcessor processor;
    private final ThreadPoolTaskExecutor parserExecutor;

    @Value("${parser.enabled:true}")
    private boolean enabled;
    @Value("${parser.batch-size:4}")
    private int batchSize;
    @Value("${parser.lease-seconds:150}")
    private int leaseSeconds;
    @Value("${parser.max-attempts:3}")
    private int maxAttempts;
    @Value("${parser.lease-recovery-backoff-seconds:15}")
    private int leaseRecoveryBackoffSeconds;

    public ExtractionWorker(ExtractionRunClaimDao claimDao, ExtractionRunRepository runs,
            ExtractionProcessor processor, @Qualifier("parserExecutor") ThreadPoolTaskExecutor parserExecutor) {
        this.claimDao = claimDao;
        this.runs = runs;
        this.processor = processor;
        this.parserExecutor = parserExecutor;
    }

    @Scheduled(fixedDelayString = "${parser.poll-interval-ms:2000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        int free = freeCapacity();
        if (free <= 0) {
            return;
        }
        List<UUID> claimed = claimDao.claimDue(Math.min(free, batchSize), leaseSeconds);
        for (UUID id : claimed) {
            parserExecutor.execute(() -> processClaimed(id));
        }
    }

    @Scheduled(fixedDelayString = "${parser.lease-recovery-ms:15000}")
    public void recoverLeases() {
        if (!enabled) {
            return;
        }
        int recovered = claimDao.recoverExpiredLeases(maxAttempts, leaseRecoveryBackoffSeconds);
        if (recovered > 0) {
            log.warn("recovered {} extraction run(s) with an expired lease", recovered);
        }
    }

    /** Load -> process (no tx held) -> lease-fenced finalize. Package-visible for tests. */
    void processClaimed(UUID id) {
        ExtractionRun run = runs.findById(id).orElse(null);
        if (run == null || run.getStatus() != ExtractionRunStatus.RUNNING || run.getLeaseToken() == null) {
            return;
        }
        UUID lease = run.getLeaseToken();

        ProcessOutcome outcome;
        try {
            outcome = processor.process(run);
        } catch (RuntimeException e) {
            log.warn("extraction processor threw for run {}: {}", id, e.toString());
            outcome = ProcessOutcome.retryable("PROCESSOR_ERROR");
        }

        boolean applied = switch (outcome.kind()) {
            case SUCCEEDED -> claimDao.finishSucceeded(id, lease, outcome.result(), outcome.warnings(),
                    run.getProgressStage());
            case NEEDS_INPUT -> claimDao.finishTerminal(id, lease, ExtractionRunStatus.NEEDS_INPUT,
                    outcome.warnings(), outcome.errorCode());
            case FAILED -> claimDao.finishTerminal(id, lease, ExtractionRunStatus.FAILED,
                    outcome.warnings(), outcome.errorCode());
            case RETRYABLE -> claimDao.scheduleRetryOrFail(id, lease, maxAttempts,
                    backoffSeconds(run.getAttemptCount()), outcome.errorCode());
        };
        if (!applied) {
            log.info("finalize for run {} was fenced out (lease changed); result discarded", id);
        }
    }

    private int backoffSeconds(int attempt) {
        return switch (Math.max(attempt, 1)) {
            case 1 -> 10;
            case 2 -> 60;
            default -> 300;
        };
    }

    private int freeCapacity() {
        var exec = parserExecutor.getThreadPoolExecutor();
        int inFlight = exec.getActiveCount() + exec.getQueue().size();
        return Math.max(0, parserExecutor.getMaxPoolSize() + 10 - inFlight);
    }

    @Transactional(readOnly = true)
    public long backlogSize() {
        return runs.count();
    }
}
