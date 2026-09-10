package com.recruitinbox.link;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.recruitinbox.parser.ExtractionRunRepository;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LinkImportApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;
    @Autowired
    ExtractionRunRepository runs;

    private UUID ownerA;
    private UUID ownerB;

    @BeforeEach
    void setUp() {
        ownerA = newUser();
        ownerB = newUser();
    }

    private UUID newUser() {
        User u = new User();
        u.setEmail("u+" + UUID.randomUUID() + "@example.com");
        return users.save(u).getId();
    }

    private String body(String url) {
        return "{\"url\":\"" + url + "\"}";
    }

    @Test
    void importsNewUrlAsAccepted() throws Exception {
        String res = mvc.perform(post("/api/v1/links")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://careers.example.com/jobs/42?utm_source=news&job=42#apply")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andExpect(jsonPath("$.duplicate", is(false)))
                .andExpect(jsonPath("$.linkId", notNullValue()))
                .andExpect(jsonPath("$.applicationId", notNullValue()))
                .andExpect(jsonPath("$.extractionRunId", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        UUID linkId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(res, "$.linkId"));
        var link = links.findByIdAndOwnerId(linkId, ownerA).orElseThrow();
        // tracking param stripped, fragment dropped
        org.assertj.core.api.Assertions.assertThat(link.getNormalizedUrl())
                .isEqualTo("https://careers.example.com/jobs/42?job=42");
        org.assertj.core.api.Assertions.assertThat(
                runs.findFirstByLinkIdAndOwnerIdOrderByGenerationDesc(linkId, ownerA)).isPresent();
    }

    @Test
    void sameOwnerSameUrlIsDuplicate() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://x.example.com/a?job=1")))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://x.example.com/a?job=1&utm_medium=email")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)))
                .andExpect(jsonPath("$.extractionRunId").doesNotExist());
    }

    @Test
    void differentOwnersGetSeparateLinksForTheSameUrl() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://y.example.com/j/9")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerB)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://y.example.com/j/9")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate", is(false)));
    }

    @Test
    void idempotencyKeyReplaysSameResponse() throws Exception {
        String k = UUID.randomUUID().toString();
        String first = mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://z.example.com/j/1")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String again = mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://z.example.com/j/1")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                (String) com.jayway.jsonpath.JsonPath.read(again, "$.extractionRunId"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(first, "$.extractionRunId"));
    }

    @Test
    void idempotencyKeyReuseWithDifferentBodyConflicts() throws Exception {
        String k = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA).header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://a.example.com/1")))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA).header("Idempotency-Key", k)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://a.example.com/2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void invalidUrlRejected() throws Exception {
        mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(body("ftp://nope.example.com/x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_URL")));
    }

    @Test
    void extractionRunIsReadableByOwnerOnly() throws Exception {
        String res = mvc.perform(post("/api/v1/links").header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(body("https://r.example.com/j/1")))
                .andReturn().getResponse().getContentAsString();
        String runId = com.jayway.jsonpath.JsonPath.read(res, "$.extractionRunId");

        mvc.perform(get("/api/v1/extractions/{id}", runId).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUEUED")));
        mvc.perform(get("/api/v1/extractions/{id}", runId).header("X-Dev-User-Id", ownerB))
                .andExpect(status().isNotFound());
    }
}
