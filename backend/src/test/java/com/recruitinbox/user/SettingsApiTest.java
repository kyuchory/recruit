package com.recruitinbox.user;

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
import com.recruitinbox.notification.NotificationChannel;
import com.recruitinbox.notification.NotificationRepository;
import com.recruitinbox.notification.NotificationStatus;
import com.recruitinbox.support.AbstractIntegrationTest;

@SpringBootTest(properties = {"notifications.enabled=false", "parser.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class SettingsApiTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("s+" + UUID.randomUUID() + "@example.com");
        owner = users.save(u).getId();
    }

    @Test
    void getReturnsDefaults() throws Exception {
        mvc.perform(get("/api/v1/settings").header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", is("Asia/Seoul")))
                .andExpect(jsonPath("$.emailEnabled", is(false)))
                .andExpect(jsonPath("$.version", is(0)));
    }

    @Test
    void patchesTimezoneAndEmailPreference() throws Exception {
        mvc.perform(patch("/api/v1/settings")
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"timezone\":\"America/New_York\",\"emailEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", is("America/New_York")))
                .andExpect(jsonPath("$.emailEnabled", is(true)))
                .andExpect(jsonPath("$.version", is(1)));
    }

    @Test
    void rejectsInvalidTimezone() throws Exception {
        mvc.perform(patch("/api/v1/settings")
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.error.fields.timezone").exists());
    }

    @Test
    void staleVersionConflicts() throws Exception {
        mvc.perform(patch("/api/v1/settings")
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":9,\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));
    }

    @Test
    void disablingEmailCancelsPendingEmailNotifications() throws Exception {
        User u = users.findById(owner).orElseThrow();
        u.setEmailVerifiedAt(Instant.now());
        u.setEmailEnabled(true);
        users.saveAndFlush(u);

        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        l = links.save(l);
        Application a = new Application();
        a.setOwnerId(owner);
        a.setLinkId(l.getId());
        a.setPositionKey("default");
        UUID appId = applications.save(a).getId();

        String at = Instant.now().plusSeconds(3L * 24 * 3600).toString();
        String ev = mvc.perform(post("/api/v1/applications/{id}/events", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CODING_TEST","scheduleKind":"EXACT","startAt":"%s","scheduledAt":"%s"}"""
                                .formatted(at, at)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(ev, "$.id"));

        mvc.perform(post("/api/v1/events/{id}/notification-rules", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","anchor":"START_AT","mode":"BEFORE_MINUTES","offsetMinutes":60}"""))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/events/{id}/confirm", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        long v = users.findById(owner).orElseThrow().getVersion();
        mvc.perform(patch("/api/v1/settings")
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + v + ",\"emailEnabled\":false}"))
                .andExpect(status().isOk());

        long pendingEmail = notifications
                .findByOwnerIdAndChannelAndStatus(owner, NotificationChannel.EMAIL, NotificationStatus.PENDING).size();
        org.assertj.core.api.Assertions.assertThat(pendingEmail).isZero();
    }
}
