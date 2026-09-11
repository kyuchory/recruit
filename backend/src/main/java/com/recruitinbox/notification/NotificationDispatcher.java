package com.recruitinbox.notification;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.application.ApplicationStatus;
import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;
import com.recruitinbox.user.UserState;

/**
 * Spring Scheduler + DB claim (v1.1 section 9.2; no RabbitMQ). A short native
 * transaction claims rows with SKIP LOCKED, then provider work runs on the
 * bounded notification executor without an open DB transaction. Finalization
 * is lease-fenced so a stale worker cannot overwrite a recovered claim.
 * {@code notifications.enabled=false} is the kill switch.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final NotificationRuleRepository rules;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final NotificationClaimDao claimDao;
    private final ThreadPoolTaskExecutor notificationExecutor;

    @Value("${notifications.enabled:true}")
    private boolean enabled;
    @Value("${notifications.batch-size:100}")
    private int batchSize;
    @Value("${notifications.lease-seconds:120}")
    private int leaseSeconds;
    @Value("${notifications.retry-backoff-seconds:300}")
    private int retryBackoffSeconds;
    @Value("${notifications.lease-recovery-backoff-seconds:30}")
    private int leaseRecoveryBackoffSeconds;

    public NotificationDispatcher(NotificationRepository notifications, ApplicationRepository applications,
            ApplicationEventRepository events,
            NotificationRuleRepository rules, UserRepository users, EmailSender emailSender,
            NotificationClaimDao claimDao,
            @Qualifier("notificationExecutor") ThreadPoolTaskExecutor notificationExecutor) {
        this.notifications = notifications;
        this.applications = applications;
        this.events = events;
        this.rules = rules;
        this.users = users;
        this.emailSender = emailSender;
        this.claimDao = claimDao;
        this.notificationExecutor = notificationExecutor;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch-interval-ms:15000}")
    public void dispatchDue() {
        if (!enabled) {
            return;
        }
        int free = freeCapacity();
        if (free <= 0) {
            return;
        }
        List<NotificationClaimDao.Claim> claimed = claimDao.claimDue(Math.min(free, batchSize), leaseSeconds);
        for (NotificationClaimDao.Claim claim : claimed) {
            notificationExecutor.execute(() -> processClaimed(claim.id(), claim.leaseToken()));
        }
    }

    @Scheduled(fixedDelayString = "${notifications.lease-recovery-ms:15000}")
    public void recoverLeases() {
        if (!enabled) {
            return;
        }
        int recovered = claimDao.recoverExpiredLeases(leaseRecoveryBackoffSeconds);
        if (recovered > 0) {
            log.warn("recovered {} notification(s) with an expired lease", recovered);
        }
    }

    /** Load and validate after the claim transaction has committed. Package-visible for tests. */
    void processClaimed(UUID id, UUID leaseToken) {
        Notification n = notifications.findById(id).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.DISPATCHING
                || !leaseToken.equals(n.getLeaseToken())) {
            return;
        }

        Instant now = Instant.now();
        if (now.isAfter(n.getExpiresAt())) {
            finish("cancel expired", n, leaseToken, claimDao.finishCancelled(id, leaseToken, now));
            return;
        }
        ApplicationEvent e = events.findByIdAndOwnerId(n.getEventId(), n.getOwnerId()).orElse(null);
        if (e == null || e.getStatus() != EventStatus.SCHEDULED
                || e.getScheduleVersion() != n.getEventScheduleVersion()) {
            finish("cancel changed event", n, leaseToken, claimDao.finishCancelled(id, leaseToken, now));
            return;
        }
        Application application = applications.findByIdAndOwnerId(e.getApplicationId(), n.getOwnerId()).orElse(null);
        if (!applicationAllows(application, e)) {
            finish("cancel ineligible application", n, leaseToken,
                    claimDao.finishCancelled(id, leaseToken, now));
            return;
        }
        NotificationRule rule = rules.findByIdAndOwnerId(n.getRuleId(), n.getOwnerId()).orElse(null);
        long currentRuleVersion = rule == null || rule.getVersion() == null ? -1L : rule.getVersion();
        if (rule == null || !rule.isEnabled() || !rule.getEventId().equals(n.getEventId())
                || currentRuleVersion != n.getRuleVersion()) {
            finish("cancel changed rule", n, leaseToken, claimDao.finishCancelled(id, leaseToken, now));
            return;
        }

        if (n.getChannel() == NotificationChannel.IN_APP) {
            finish("publish in-app", n, leaseToken, claimDao.finishInApp(id, leaseToken, now));
            return;
        }

        if (n.getChannel() != NotificationChannel.EMAIL) {
            finish("fail unsupported channel", n, leaseToken, claimDao.finishFailed(id, leaseToken, now));
            return;
        }

        User owner = users.findById(n.getOwnerId()).orElse(null);
        if (owner == null || owner.getState() != UserState.ACTIVE
                || owner.getEmail() == null || !owner.isEmailEnabled()
                || owner.getEmailVerifiedAt() == null || owner.getEmailSuppressedAt() != null) {
            finish("fail ineligible email", n, leaseToken, claimDao.finishFailed(id, leaseToken, now));
            log.info("notification {} skipped: no eligible email for owner", n.getId());
            return;
        }

        EmailSender.Result result;
        try {
            var context = new LinkedHashMap<>(n.getPayload());
            context.put("idempotencyKey", n.getIdempotencyKey());
            result = emailSender.send(owner.getEmail(), subject(n), body(n), context);
        } catch (RuntimeException ex) {
            log.warn("email sender threw for notification {}: {}", id, ex.toString());
            finish("release after sender error", n, leaseToken,
                    claimDao.releaseForRetry(id, leaseToken, retryBackoffSeconds));
            return;
        }

        switch (result.outcome()) {
            case ACCEPTED -> finish("complete email", n, leaseToken,
                    claimDao.finishCompleted(id, leaseToken, now));
            case RETRYABLE -> finish("release retryable email", n, leaseToken,
                    claimDao.releaseForRetry(id, leaseToken, retryBackoffSeconds));
            case FAILED -> finish("fail email", n, leaseToken,
                    claimDao.finishFailed(id, leaseToken, now));
        }
    }

    private void finish(String operation, Notification n, UUID leaseToken, boolean applied) {
        if (!applied) {
            log.info("{} for notification {} was fenced out (lease {} is stale)",
                    operation, n.getId(), leaseToken);
        }
    }

    private int freeCapacity() {
        var executor = notificationExecutor.getThreadPoolExecutor();
        int capacity = notificationExecutor.getMaxPoolSize() + notificationExecutor.getQueueCapacity();
        return Math.max(0, capacity - executor.getActiveCount() - executor.getQueue().size());
    }

    private boolean applicationAllows(Application application, ApplicationEvent event) {
        if (application == null || application.getArchivedAt() != null
                || application.getStatus() == ApplicationStatus.REJECTED
                || application.getStatus() == ApplicationStatus.WITHDRAWN) {
            return false;
        }
        return (application.getStatus() != ApplicationStatus.APPLIED
                && application.getStatus() != ApplicationStatus.IN_PROGRESS)
                || event.getType() != ApplicationEventType.DOCUMENT_DEADLINE;
    }

    private String subject(Notification n) {
        Object label = n.getPayload().getOrDefault("customLabel", null);
        Object type = n.getPayload().getOrDefault("eventType", "채용 일정");
        return "채용 일정 알림: " + (label != null ? label : type);
    }

    private String body(Notification n) {
        return "저장·확인한 일정 기준 알림입니다. 앱에서 최신 일정을 확인하세요.";
    }
}
