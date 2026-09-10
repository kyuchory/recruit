package com.recruitinbox.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

/**
 * Spring Scheduler + DB claim (v1.1 section 9.2; no RabbitMQ). Every tick:
 * pick due PENDING notifications, re-validate the event/rule version snapshot,
 * then dispatch -- IN_APP by revealing it in the inbox, EMAIL via {@link EmailSender}.
 * at-least-once; the unique idempotency key prevents duplicate materialization.
 * {@code notifications.enabled=false} is the kill switch.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final ApplicationEventRepository events;
    private final NotificationRuleRepository rules;
    private final UserRepository users;
    private final EmailSender emailSender;

    @Value("${notifications.enabled:true}")
    private boolean enabled;
    @Value("${notifications.batch-size:100}")
    private int batchSize;

    public NotificationDispatcher(NotificationRepository notifications, ApplicationEventRepository events,
            NotificationRuleRepository rules, UserRepository users, EmailSender emailSender) {
        this.notifications = notifications;
        this.events = events;
        this.rules = rules;
        this.users = users;
        this.emailSender = emailSender;
    }

    @Scheduled(fixedDelayString = "${notifications.dispatch-interval-ms:15000}")
    @Transactional
    public void dispatchDue() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        List<Notification> due = notifications
                .findByStatusAndScheduledSendAtLessThanEqualAndNextAttemptAtLessThanEqual(
                        NotificationStatus.PENDING, now, now);
        int processed = 0;
        for (Notification n : due) {
            if (processed++ >= batchSize) {
                break;
            }
            dispatchOne(n, now);
        }
    }

    /** Package-visible for tests. */
    @Transactional
    void dispatchOne(Notification n, Instant now) {
        if (now.isAfter(n.getExpiresAt())) {
            cancel(n, "EXPIRED");
            return;
        }
        ApplicationEvent e = events.findById(n.getEventId()).orElse(null);
        if (e == null || e.getStatus() != EventStatus.SCHEDULED
                || e.getScheduleVersion() != n.getEventScheduleVersion()) {
            cancel(n, "EVENT_CHANGED");
            return;
        }
        long currentRuleVersion = rules.findById(n.getRuleId())
                .map(r -> r.getVersion() == null ? 0L : r.getVersion()).orElse(-1L);
        if (currentRuleVersion != n.getRuleVersion()) {
            cancel(n, "RULE_CHANGED");
            return;
        }

        if (n.getChannel() == NotificationChannel.IN_APP) {
            n.setVisibleAt(now);
            n.setStatus(NotificationStatus.COMPLETED);
            n.setCompletedAt(now);
            notifications.save(n);
            return;
        }

        // EMAIL
        User owner = users.findById(n.getOwnerId()).orElse(null);
        if (owner == null || owner.getEmail() == null || !owner.isEmailEnabled()
                || owner.getEmailVerifiedAt() == null || owner.getEmailSuppressedAt() != null) {
            n.setStatus(NotificationStatus.FAILED);
            n.setCompletedAt(now);
            notifications.save(n);
            log.info("notification {} skipped: no eligible email for owner", n.getId());
            return;
        }
        EmailSender.Result result = emailSender.send(owner.getEmail(),
                subject(n), body(n), n.getPayload());
        switch (result.outcome()) {
            case ACCEPTED -> {
                n.setStatus(NotificationStatus.COMPLETED);
                n.setCompletedAt(now);
            }
            case RETRYABLE -> n.setNextAttemptAt(now.plus(backoffMinutes(n), ChronoUnit.MINUTES));
            case FAILED -> {
                n.setStatus(NotificationStatus.FAILED);
                n.setCompletedAt(now);
            }
            default -> { }
        }
        notifications.save(n);
    }

    private void cancel(Notification n, String reason) {
        n.setStatus(NotificationStatus.CANCELLED);
        n.setCompletedAt(Instant.now());
        notifications.save(n);
        log.debug("notification {} cancelled: {}", n.getId(), reason);
    }

    private long backoffMinutes(Notification n) {
        return 5L;
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
