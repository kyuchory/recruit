package com.recruitinbox.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.recruitinbox.support.AbstractIntegrationTest;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

/**
 * v1.1 section 9.1 status-driven cancellation policy:
 * <ul>
 *   <li>APPLIED / IN_PROGRESS -&gt; DOCUMENT_DEADLINE notifications only</li>
 *   <li>REJECTED / WITHDRAWN, archive, delete -&gt; all pending</li>
 *   <li>event COMPLETED / CANCELLED -&gt; that event only, siblings untouched</li>
 *   <li>un-archive -&gt; still-future rules re-materialize</li>
 * </ul>
 */
@SpringBootTest(properties = {"notifications.enabled=false", "parser.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class NotificationCancellationPolicyTest extends AbstractIntegrationTest {

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

    private UUID owner;
    private UUID appId;
    private UUID docEventId;
    private UUID codingEventId;

    @BeforeEach
    void setUp() throws Exception {
        User u = new User();
        u.setEmail("c+" + UUID.randomUUID() + "@example.com");
        owner = users.save(u).getId();

        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        l = links.save(l);

        Application a = new Application();
        a.setOwnerId(owner);
        a.setLinkId(l.getId());
        a.setPositionKey("default");
        appId = applications.save(a).getId();

        docEventId = scheduledEventWithRule("DOCUMENT_DEADLINE");
        codingEventId = scheduledEventWithRule("CODING_TEST");
    }

    private UUID scheduledEventWithRule(String type) throws Exception {
        String at = Instant.now().plusSeconds(3L * 24 * 3600).toString();
        String ev = mvc.perform(post("/api/v1/applications/{id}/events", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"%s","scheduleKind":"EXACT","startAt":"%s","scheduledAt":"%s"}"""
                                .formatted(type, at, at)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(ev, "$.id"));

        mvc.perform(post("/api/v1/events/{id}/notification-rules", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"IN_APP","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":60}"""))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/events/{id}/confirm", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        return eventId;
    }

    private long pending(UUID eventId) {
        return notifications.findByEventIdAndOwnerId(eventId, owner).stream()
                .filter(n -> n.getStatus() == NotificationStatus.PENDING).count();
    }

    private void patchApp(String body) throws Exception {
        long v = applications.findById(appId).orElseThrow().getVersion();
        mvc.perform(patch("/api/v1/applications/{id}", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"expectedVersion\":0", "\"expectedVersion\":" + v)))
                .andExpect(status().isOk());
    }

    @Test
    void baseline() {
        assertThat(pending(docEventId)).isEqualTo(1);
        assertThat(pending(codingEventId)).isEqualTo(1);
    }

    @Test
    void appliedCancelsOnlyDocumentDeadline() throws Exception {
        patchApp("{\"expectedVersion\":0,\"status\":\"APPLIED\"}");
        assertThat(pending(docEventId)).isZero();
        assertThat(pending(codingEventId)).isEqualTo(1);
    }

    @Test
    void rejectedCancelsEverything() throws Exception {
        patchApp("{\"expectedVersion\":0,\"status\":\"REJECTED\"}");
        assertThat(pending(docEventId)).isZero();
        assertThat(pending(codingEventId)).isZero();
    }

    @Test
    void archiveCancelsAllAndUnarchiveReschedules() throws Exception {
        patchApp("{\"expectedVersion\":0,\"archived\":true}");
        assertThat(pending(docEventId)).isZero();
        assertThat(pending(codingEventId)).isZero();

        patchApp("{\"expectedVersion\":0,\"archived\":false}");
        assertThat(pending(docEventId)).isEqualTo(1);
        assertThat(pending(codingEventId)).isEqualTo(1);
    }

    @Test
    void eventCompletedCancelsOnlyThatEvent() throws Exception {
        // event @Version is 1 after confirm's saveAndFlush
        mvc.perform(patch("/api/v1/events/{id}", docEventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());
        assertThat(pending(docEventId)).isZero();
        assertThat(pending(codingEventId)).isEqualTo(1);
    }
}
