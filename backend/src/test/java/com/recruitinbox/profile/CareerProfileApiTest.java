package com.recruitinbox.profile;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.recruitinbox.support.AbstractIntegrationTest;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CareerProfileApiTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private UUID owner;
    private UUID otherOwner;

    @BeforeEach
    void setUp() {
        owner = newUser();
        otherOwner = newUser();
    }

    @Test
    void createsAndFiltersStructuredProfileItems() throws Exception {
        create("""
                {"category":"CERTIFICATION","label":"정보처리기사","fields":{"grade":"기사"},"valueText":"취득 완료",
                 "startedOn":"2026-01-02","sensitive":false}""");
        create("""
                {"category":"PROJECT","label":"개인 프로젝트","details":"설계부터 운영까지 담당"}""");

        mvc.perform(get("/api/v1/profile-items").param("category", "CERTIFICATION")
                        .header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("정보처리기사")))
                .andExpect(jsonPath("$[0].fields.grade", is("기사")))
                .andExpect(jsonPath("$[0].startedOn", is("2026-01-02")));
    }

    @Test
    void updatesWithOptimisticVersionAndValidatesDateRange() throws Exception {
        String id = create("""
                {"category":"CAREER","label":"개발팀","startedOn":"2025-04-01"}""");
        mvc.perform(patch("/api/v1/profile-items/{id}", id)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"valueText":"프론트엔드 개발","endedOn":"2025-08-29"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.valueText", is("프론트엔드 개발")));

        mvc.perform(patch("/api/v1/profile-items/{id}", id)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":1,"startedOn":"2026-01-01","endedOn":"2025-01-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    @Test
    void doesNotExposeAnotherUsersProfileItems() throws Exception {
        create("{\"category\":\"PERSONAL\",\"label\":\"휴대폰\",\"valueText\":\"010-0000-0000\",\"sensitive\":true}");
        mvc.perform(get("/api/v1/profile-items").header("X-Dev-User-Id", otherOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void storesAndReturnsCategorySpecificFieldsWithDerivedLabel() throws Exception {
        String id = create("""
                {"category":"CAREER","fields":{"company":"테스트회사","jobTitle":"백엔드 개발",
                 "annualSalary":"4,000만원","startDate":"2025-01-01","leaveReason":"성장 환경 변경"}}""");

        mvc.perform(get("/api/v1/profile-items").param("category", "CAREER")
                        .header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label", is("테스트회사")))
                .andExpect(jsonPath("$[0].fields.jobTitle", is("백엔드 개발")))
                .andExpect(jsonPath("$[0].fields.annualSalary", is("4,000만원")));

        String stored = jdbc.queryForObject("SELECT field_values FROM career_profile_items WHERE id = ?::uuid",
                String.class, id);
        assertThat(stored).startsWith("enc:v1:").doesNotContain("테스트회사");
    }

    @Test
    void rejectsFieldsThatDoNotBelongToTheSelectedCategory() throws Exception {
        mvc.perform(post("/api/v1/profile-items")
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"PERSONAL","fields":{"company":"잘못된 필드"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    private String create(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/profile-items")
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private UUID newUser() {
        User user = new User();
        user.setEmail("profile+" + UUID.randomUUID() + "@example.com");
        return users.save(user).getId();
    }
}
