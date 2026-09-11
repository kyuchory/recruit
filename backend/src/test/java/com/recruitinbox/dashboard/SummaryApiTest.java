package com.recruitinbox.dashboard;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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

@SpringBootTest(properties = {"notifications.enabled=false", "parser.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class SummaryApiTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ApplicationRepository applications;

    private UUID owner;
    private UUID app1;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("d+" + UUID.randomUUID() + "@example.com");
        owner = users.save(u).getId();
        app1 = newApp();
        newApp(); // a second PENDING-review application
    }

    private UUID newApp() {
        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        l.setOriginalUrl("https://careers.example.com/jobs/" + UUID.randomUUID());
        l = links.save(l);
        Application a = new Application();
        a.setOwnerId(owner);
        a.setLinkId(l.getId());
        a.setPositionKey("default");
        a.setCompanyName("예시회사");
        return applications.save(a).getId();
    }

    private void scheduledEvent(String isoAt) throws Exception {
        String ev = mvc.perform(post("/api/v1/applications/{id}/events", app1)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CODING_TEST","scheduleKind":"EXACT","scheduledAt":"%s","startAt":"%s"}"""
                                .formatted(isoAt, isoAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(ev, "$.id"));
        mvc.perform(post("/api/v1/events/{id}/confirm", eventId)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void cardsReflectScheduleAndReview() throws Exception {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(seoul);
        scheduledEvent(today.atTime(23, 0).atZone(seoul).toInstant().toString());   // today + this week
        scheduledEvent(today.plusDays(20).atTime(12, 0).atZone(seoul).toInstant().toString()); // neither

        mvc.perform(get("/api/v1/summary").header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", is("Asia/Seoul")))
                .andExpect(jsonPath("$.todayCount", is(1)))
                .andExpect(jsonPath("$.today[0].type", is("CODING_TEST")))
                .andExpect(jsonPath("$.today[0].companyName", is("예시회사")))
                .andExpect(jsonPath("$.thisWeekCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.needsReviewCount", is(2)))
                .andExpect(jsonPath("$.needsReview[0].companyName", is("예시회사")));
    }

    @Test
    void emptyWhenNothingScheduled() throws Exception {
        mvc.perform(get("/api/v1/summary").header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCount", is(0)))
                .andExpect(jsonPath("$.thisWeekCount", is(0)))
                .andExpect(jsonPath("$.needsReviewCount", is(2)));
    }
}
