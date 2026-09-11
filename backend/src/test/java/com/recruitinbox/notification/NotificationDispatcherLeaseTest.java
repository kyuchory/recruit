package com.recruitinbox.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

/** Uses committed rows so independent worker connections exercise real claims. */
@SpringBootTest(properties = {"notifications.enabled=false", "parser.enabled=false"})
class NotificationDispatcherLeaseTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    NotificationClaimDao claimDao;
    @Autowired
    NotificationDispatcher dispatcher;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    NotificationRuleRepository rules;
    @Autowired
    ApplicationEventRepository events;
    @Autowired
    ApplicationRepository applications;
    @Autowired
    LinkRepository links;
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    EmailSender emailSender;

    private UUID ownerId;
    private UUID applicationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setEmail("lease+" + UUID.randomUUID() + "@example.com");
        owner.setEmailVerifiedAt(Instant.now());
        owner.setEmailEnabled(true);
        ownerId = users.save(owner).getId();

        Link link = new Link();
        link.setOwnerId(ownerId);
        link.setSourceChannel("manual");
        link = links.save(link);

        Application application = new Application();
        application.setOwnerId(ownerId);
        application.setLinkId(link.getId());
        application.setPositionKey("default");
        application = applications.save(application);
        applicationId = application.getId();

        Instant eventTime = Instant.now().plus(1, ChronoUnit.DAYS);
        ApplicationEvent event = new ApplicationEvent();
        event.setOwnerId(ownerId);
        event.setApplicationId(application.getId());
        event.setType(ApplicationEventType.CODING_TEST);
        event.setScheduleKind(ScheduleKind.EXACT);
        event.setScheduledAt(eventTime);
        event.setStartAt(eventTime);
        event.setStatus(EventStatus.SCHEDULED);
        event.setConfirmedAt(Instant.now());
        event.setConfirmedBy(ownerId);
        event = events.save(event);

        NotificationRule rule = new NotificationRule();
        rule.setOwnerId(ownerId);
        rule.setEventId(event.getId());
        rule.setChannel(NotificationChannel.EMAIL);
        rule.setAnchor(NotificationAnchor.START_AT);
        rule.setMode(NotificationMode.BEFORE_MINUTES);
        rule.setOffsetMinutes(0);
        rule = rules.save(rule);

        notification = new Notification();
        notification.setOwnerId(ownerId);
        notification.setEventId(event.getId());
        notification.setRuleId(rule.getId());
        notification.setEventScheduleVersion(event.getScheduleVersion());
        notification.setRuleVersion(rule.getVersion());
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setScheduledSendAt(Instant.now().minusSeconds(10));
        notification.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        notification.setIdempotencyKey("notification-test-" + UUID.randomUUID());
        notification.setNextAttemptAt(Instant.now().minusSeconds(10));
        notification = notifications.save(notification);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notifications WHERE owner_id = ?", ownerId);
        jdbc.update("DELETE FROM notification_rules WHERE owner_id = ?", ownerId);
        jdbc.update("DELETE FROM application_events WHERE owner_id = ?", ownerId);
        jdbc.update("DELETE FROM applications WHERE owner_id = ?", ownerId);
        jdbc.update("DELETE FROM links WHERE owner_id = ?", ownerId);
        jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
    }

    @Test
    void concurrentWorkersClaimTheDueNotificationOnlyOnce() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await();
                return claimDao.claimDue(10, 120);
            });
            var second = pool.submit(() -> {
                start.await();
                return claimDao.claimDue(10, 120);
            });
            start.countDown();

            List<NotificationClaimDao.Claim> all = new java.util.ArrayList<>();
            all.addAll(first.get());
            all.addAll(second.get());
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().id()).isEqualTo(notification.getId());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleWorkerCannotFinalizeAfterItsLeaseChanges() {
        NotificationClaimDao.Claim claim = claimOne();
        jdbc.update("UPDATE notifications SET lease_token = gen_random_uuid() WHERE id = ?", claim.id());

        assertThat(claimDao.finishCompleted(claim.id(), claim.leaseToken(), Instant.now())).isFalse();
        assertThat(notifications.findById(claim.id()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.DISPATCHING);
    }

    @Test
    void expiredLeaseReturnsToPendingWithBackoff() {
        NotificationClaimDao.Claim claim = claimOne();
        jdbc.update("UPDATE notifications SET lease_until = now() - interval '1 minute' WHERE id = ?", claim.id());
        Instant beforeRecovery = Instant.now();

        assertThat(claimDao.recoverExpiredLeases(30)).isEqualTo(1);
        Notification recovered = notifications.findById(claim.id()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(recovered.getLeaseToken()).isNull();
        assertThat(recovered.getNextAttemptAt()).isAfter(beforeRecovery);
    }

    @Test
    void acceptedEmailCompletesWithAStableProviderIdempotencyKey() {
        when(emailSender.send(anyString(), anyString(), anyString(), any()))
                .thenReturn(EmailSender.Result.accepted("provider-message"));
        NotificationClaimDao.Claim claim = claimOne();

        dispatcher.processClaimed(claim.id(), claim.leaseToken());

        Notification completed = notifications.findById(claim.id()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(NotificationStatus.COMPLETED);
        assertThat(completed.getLeaseToken()).isNull();
        verify(emailSender).send(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.argThat((Map<String, Object> context) ->
                        notification.getIdempotencyKey().equals(context.get("idempotencyKey"))));
    }

    @Test
    void retryableEmailReleasesTheClaimWithBackoff() {
        when(emailSender.send(anyString(), anyString(), anyString(), any()))
                .thenReturn(EmailSender.Result.retryable("TEMPORARY"));
        NotificationClaimDao.Claim claim = claimOne();
        Instant beforeDispatch = Instant.now();

        dispatcher.processClaimed(claim.id(), claim.leaseToken());

        Notification retry = notifications.findById(claim.id()).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(retry.getLeaseToken()).isNull();
        assertThat(retry.getNextAttemptAt()).isAfter(beforeDispatch);
    }

    @Test
    void archivedApplicationIsCancelledBeforeCallingTheProvider() {
        Application application = applications.findByIdAndOwnerId(applicationId, ownerId).orElseThrow();
        application.setArchivedAt(Instant.now());
        applications.save(application);
        NotificationClaimDao.Claim claim = claimOne();

        dispatcher.processClaimed(claim.id(), claim.leaseToken());

        assertThat(notifications.findById(claim.id()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.CANCELLED);
        verifyNoInteractions(emailSender);
    }

    private NotificationClaimDao.Claim claimOne() {
        return claimDao.claimDue(1, 120).getFirst();
    }
}
