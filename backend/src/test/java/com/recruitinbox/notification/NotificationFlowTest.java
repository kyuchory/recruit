package com.recruitinbox.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

@SpringBootTest(properties = {"notifications.enabled=false", "parser.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class NotificationFlowTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ApplicationRepository applications;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    NotificationDispatcher dispatcher;

    private UUID owner;
    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        User u = new User();
        u.setEmail("n+" + UUID.randomUUID() + "@example.com");
        u.setEmailVerifiedAt(Instant.now());
        u.setEmailEnabled(true);
        owner = users.save(u).getId();

        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        l = links.save(l);
        Application a = new Application();
        a.setOwnerId(owner);
        a.setLinkId(l.getId());
        a.setPositionKey("default");
        UUID appId = applications.save(a).getId();

        String start = Instant.now().plusSeconds(3 * 24 * 3600).toString();
        String ev = mvc.perform(post("/api/v1/applications/{id}/events", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CODING_TEST","scheduleKind":"EXACT","startAt":"%s","scheduledAt":"%s"}"""
                                .formatted(start, start)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(ev, "$.id"));
    }

    private void addRule(String body) throws Exception {
        mvc.perform(post("/api/v1/events/{id}/notification-rules", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void ruleValidationAndChannelPolicy() throws Exception {
        mvc.perform(post("/api/v1/events/{id}/notification-rules", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":99999}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));

        mvc.perform(post("/api/v1/events/{id}/notification-rules", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"WEB_PUSH","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":60}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("CHANNEL_NOT_AVAILABLE")));
    }

    @Test
    void confirmMaterializesRulesAndScheduleChangeCancelsThem() throws Exception {
        addRule("""
                {"channel":"EMAIL","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":1440}""");
        addRule("""
                {"channel":"IN_APP","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":30}""");

        // not confirmed yet -> nothing materialized
        mvc.perform(get("/api/v1/events/{id}/notifications", eventId).header("X-Dev-User-Id", owner))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/v1/events/{id}/confirm", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SCHEDULED")))
                .andExpect(jsonPath("$.confirmedAt").exists());

        mvc.perform(get("/api/v1/events/{id}/notifications", eventId).header("X-Dev-User-Id", owner))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].deliveryStatus", is("SCHEDULED")));

        // change the schedule -> confirmation reset + pending notifications cancelled
        String later = Instant.now().plusSeconds(5 * 24 * 3600).toString();
        mvc.perform(patch("/api/v1/events/{id}", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"startAt":"%s","scheduledAt":"%s"}""".formatted(later, later)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UNSCHEDULED")));

        long pending = notifications.findByEventIdAndOwnerId(eventId, owner).stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING).count();
        org.assertj.core.api.Assertions.assertThat(pending).isZero();
    }

    @Test
    void dispatchRevealsInAppAndSendsEmailThenVersionMismatchCancels() throws Exception {
        addRule("""
                {"channel":"IN_APP","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":30}""");
        addRule("""
                {"channel":"EMAIL","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":60}""");
        mvc.perform(post("/api/v1/events/{id}/confirm", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        // make both notifications due now (same tx as the test)
        Instant past = Instant.now().minusSeconds(60);
        var pendingNow = notifications.findByEventIdAndOwnerId(eventId, owner);
        pendingNow.forEach(n -> {
            n.setScheduledSendAt(past);
            n.setNextAttemptAt(past);
        });
        notifications.saveAll(pendingNow);
        notifications.flush();

        Instant now = Instant.now();
        notifications.findByEventIdAndOwnerId(eventId, owner)
                .forEach(n -> dispatcher.dispatchOne(n, now));

        var all = notifications.findByEventIdAndOwnerId(eventId, owner);
        org.assertj.core.api.Assertions.assertThat(all).allSatisfy(n ->
                org.assertj.core.api.Assertions.assertThat(n.getStatus()).isEqualTo(NotificationStatus.COMPLETED));
        org.assertj.core.api.Assertions.assertThat(all).anySatisfy(n -> {
            if (n.getChannel() == NotificationChannel.IN_APP) {
                org.assertj.core.api.Assertions.assertThat(n.getVisibleAt()).isNotNull();
            }
        });

        mvc.perform(get("/api/v1/notifications").header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.unreadCount", is(1)));
    }
}
