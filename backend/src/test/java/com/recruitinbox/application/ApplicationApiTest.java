package com.recruitinbox.application;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class ApplicationApiTest extends com.recruitinbox.support.AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    LinkRepository links;

    private UUID ownerA;
    private UUID ownerB;
    private UUID linkA;

    @BeforeEach
    void setUp() {
        ownerA = newUser().getId();
        ownerB = newUser().getId();
        linkA = newLink(ownerA).getId();
    }

    private User newUser() {
        User u = new User();
        u.setEmail("u+" + UUID.randomUUID() + "@example.com");
        return users.save(u);
    }

    private Link newLink(UUID owner) {
        Link l = new Link();
        l.setOwnerId(owner);
        l.setSourceChannel("url");
        l.setOriginalUrl("https://careers.example.com/jobs/" + UUID.randomUUID());
        return links.save(l);
    }

    private String createBody(UUID linkId, String positionKey) {
        return """
                {"linkId":"%s","positionKey":"%s","companyName":"예시회사","positionTitle":"백엔드"}
                """.formatted(linkId, positionKey);
    }

    @Test
    void crudLifecycle() throws Exception {
        String location = mvc.perform(post("/api/v1/applications")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(linkA, "default")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("INTERESTED")))
                .andExpect(jsonPath("$.reviewStatus", is("PENDING")))
                .andExpect(jsonPath("$.version", is(0)))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(location, "$.id");

        mvc.perform(get("/api/v1/applications/{id}", id).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName", is("예시회사")))
                .andExpect(jsonPath("$.sourceUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://careers.example.com/jobs/")));

        mvc.perform(patch("/api/v1/applications/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"sourceUrl":"https://jobs.example.com/opening/42?utm_source=test",
                                 "status":"APPLIED","notes":"제출 완료"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.sourceUrl", is("https://jobs.example.com/opening/42?utm_source=test")))
                .andExpect(jsonPath("$.appliedAt").exists())
                .andExpect(jsonPath("$.version", is(1)));

        mvc.perform(get("/api/v1/links/{id}", linkA).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl", is("https://jobs.example.com/opening/42?utm_source=test")));

        mvc.perform(delete("/api/v1/applications/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/applications/{id}", id).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void ownerIsolation() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/applications")
                                .header("X-Dev-User-Id", ownerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(linkA, "default")))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        mvc.perform(get("/api/v1/applications/{id}", id).header("X-Dev-User-Id", ownerB))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/applications/{id}", id)
                        .header("X-Dev-User-Id", ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"notes":"hijack"}"""))
                .andExpect(status().isNotFound());
        // ownerB cannot create against ownerA's link either
        mvc.perform(post("/api/v1/applications")
                        .header("X-Dev-User-Id", ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(linkA, "default")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationFailure() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"no link id"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.error.fields.linkId").exists());
    }

    @Test
    void createsManualApplicationWithoutAUrl() throws Exception {
        String body = mvc.perform(post("/api/v1/applications/manual")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"수동 회사","positionTitle":"백엔드 개발자"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName", is("수동 회사")))
                .andExpect(jsonPath("$.positionTitle", is("백엔드 개발자")))
                .andExpect(jsonPath("$.reviewStatus", is("NOT_REQUIRED")))
                .andReturn().getResponse().getContentAsString();

        String linkId = com.jayway.jsonpath.JsonPath.read(body, "$.linkId");
        mvc.perform(get("/api/v1/links/{id}", linkId).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.latestRun").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void manualApplicationRequiresCompanyAndPosition() throws Exception {
        mvc.perform(post("/api/v1/applications/manual")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"","positionTitle":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.error.fields.companyName").exists())
                .andExpect(jsonPath("$.error.fields.positionTitle").exists());
    }

    @Test
    void createsManualApplicationWithAnOptionalSourceUrl() throws Exception {
        mvc.perform(post("/api/v1/applications/manual")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"수동 회사","positionTitle":"프론트엔드 개발자",
                                 "sourceUrl":"https://careers.example.org/jobs/123?utm_campaign=test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceUrl",
                        is("https://careers.example.org/jobs/123?utm_campaign=test")));
    }

    @Test
    void versionConflictAndPreconditionRequired() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post("/api/v1/applications")
                                .header("X-Dev-User-Id", ownerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody(linkA, "default")))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        mvc.perform(patch("/api/v1/applications/{id}", id)
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":99,"notes":"stale"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("VERSION_CONFLICT")));

        mvc.perform(delete("/api/v1/applications/{id}", id).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void duplicatePositionKeyRejected() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(linkA, "default")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/applications")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(linkA, "default")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DUPLICATE")));
    }

    @Test
    void listIsPagedAndOwnerScoped() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/applications")
                            .header("X-Dev-User-Id", ownerA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(newLink(ownerA).getId(), "default")))
                    .andExpect(status().isCreated());
        }
        // list query runs inside the same test transaction, so it sees the inserts
        mvc.perform(get("/api/v1/applications?page=0&size=2").header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)));

        mvc.perform(get("/api/v1/applications").header("X-Dev-User-Id", ownerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
