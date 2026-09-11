package com.recruitinbox.applicationevent;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationEventApiTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ApplicationRepository applications;

    private UUID ownerA;
    private UUID ownerB;
    private UUID appA;

    @BeforeEach
    void setUp() {
        ownerA = newUser();
        ownerB = newUser();
        Link l = new Link();
        l.setOwnerId(ownerA);
        l.setSourceChannel("url");
        l = links.save(l);
        Application a = new Application();
        a.setOwnerId(ownerA);
        a.setLinkId(l.getId());
        a.setPositionKey("default");
        appA = applications.save(a).getId();
    }

    private UUID newUser() {
        User u = new User();
        u.setEmail("u+" + UUID.randomUUID() + "@example.com");
        return users.save(u).getId();
    }

    private String create(String body) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/applications/{id}/events", appA)
                                .header("X-Dev-User-Id", ownerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    @Test
    void createsPlaceholderAndLists() throws Exception {
        create("""
                {"type":"CODING_TEST","scheduleKind":"UNKNOWN"}""");
        create("""
                {"type":"CUSTOM","customLabel":"사전 인적성","scheduleKind":"UNKNOWN","sortOrder":1}""");

        mvc.perform(get("/api/v1/applications/{id}/events", appA).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type", is("CODING_TEST")))
                .andExpect(jsonPath("$[1].customLabel", is("사전 인적성")));
    }

    @Test
    void customWithoutLabelIsRejected() throws Exception {
        mvc.perform(post("/api/v1/applications/{id}/events", appA)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CUSTOM","scheduleKind":"UNKNOWN"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    void dateOnlyWithTimeIsAmbiguous() throws Exception {
        mvc.perform(post("/api/v1/applications/{id}/events", appA)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DOCUMENT_DEADLINE","scheduleKind":"DATE_ONLY",
                                 "scheduledDate":"2026-09-18","scheduledAt":"2026-09-18T08:00:00Z"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("AMBIGUOUS_SCHEDULE")));
    }

    @Test
    void notesEditKeepsScheduleVersionButBumpsVersion() throws Exception {
        String id = create("""
                {"type":"CODING_TEST","scheduleKind":"EXACT","startAt":"2026-09-28T04:00:00Z",
                 "scheduledAt":"2026-09-28T04:00:00Z"}""");

        mvc.perform(patch("/api/v1/events/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"notes":"화상 링크 도착","result":"WAITING"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleVersion", is(1)))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.result", is("WAITING")));
    }

    @Test
    void scheduleEditBumpsScheduleVersionAndResetsConfirmation() throws Exception {
        String id = create("""
                {"type":"CODING_TEST","scheduleKind":"EXACT","startAt":"2026-09-28T04:00:00Z",
                 "scheduledAt":"2026-09-28T04:00:00Z","status":"SCHEDULED"}""");

        mvc.perform(patch("/api/v1/events/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"startAt":"2026-09-29T04:00:00Z",
                                 "scheduledAt":"2026-09-29T04:00:00Z"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleVersion", is(2)))
                .andExpect(jsonPath("$.status", is("UNSCHEDULED")))
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());
    }

    @Test
    void ownerIsolationAndDelete() throws Exception {
        String id = create("""
                {"type":"NCS","scheduleKind":"UNKNOWN"}""");

        mvc.perform(get("/api/v1/events/{id}", id).header("X-Dev-User-Id", ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/applications/{id}/events", appA).header("X-Dev-User-Id", ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/events/{id}", id)
                        .header("X-Dev-User-Id", ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"notes":"x"}"""))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/events/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .header("If-Match", "0"))
                .andExpect(status().isNoContent());
    }
}
