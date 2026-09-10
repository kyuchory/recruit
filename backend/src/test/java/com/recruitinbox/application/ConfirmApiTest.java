package com.recruitinbox.application;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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

import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfirmApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ApplicationRepository applications;

    private UUID owner;
    private UUID appId;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("cf+" + UUID.randomUUID() + "@example.com");
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
    }

    @Test
    void confirmAppliesProposalAndCreatesPlaceholderEvents() throws Exception {
        mvc.perform(post("/api/v1/applications/{id}/confirm", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"companyName":"AI가 뽑은 회사","positionTitle":"백엔드",
                                 "placeholderCandidates":[{"type":"CODING_TEST","sourceCandidateId":"c1"},
                                                          {"type":"INTERVIEW_1","sourceCandidateId":"c2"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.companyName", is("AI가 뽑은 회사")))
                .andExpect(jsonPath("$.application.reviewStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.application.fieldMeta.companyName.source", is("RUN")))
                .andExpect(jsonPath("$.createdEvents", hasSize(2)))
                .andExpect(jsonPath("$.createdEvents[0].scheduleKind", is("UNKNOWN")))
                .andExpect(jsonPath("$.createdEvents[0].status", is("UNSCHEDULED")));

        // re-confirm with the same candidate ids -> no duplicate events
        mvc.perform(post("/api/v1/applications/{id}/confirm", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,
                                 "placeholderCandidates":[{"type":"CODING_TEST","sourceCandidateId":"c1"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdEvents", hasSize(0)));

        mvc.perform(get("/api/v1/applications/{id}/events", appId).header("X-Dev-User-Id", owner))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void confirmNeverOverwritesAUserEditedField() throws Exception {
        mvc.perform(patch("/api/v1/applications/{id}", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"companyName":"내가 직접 고친 회사"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldMeta.companyName.source", is("USER")));

        mvc.perform(post("/api/v1/applications/{id}/confirm", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"companyName":"AI가 덮어쓰려는 값","positionTitle":"백엔드"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.companyName", is("내가 직접 고친 회사")))
                .andExpect(jsonPath("$.application.positionTitle", is("백엔드")))
                .andExpect(jsonPath("$.application.fieldMeta.companyName.source", is("USER")));
    }

    @Test
    void confirmRejectsStaleVersion() throws Exception {
        mvc.perform(post("/api/v1/applications/{id}/confirm", appId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":42,"companyName":"x"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));
    }
}
