package com.recruitinbox.essay;

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

import com.jayway.jsonpath.JsonPath;
import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.support.AbstractIntegrationTest;
import com.recruitinbox.user.User;
import com.recruitinbox.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EssayApiTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired LinkRepository links;
    @Autowired ApplicationRepository applications;

    private UUID owner;
    private UUID otherOwner;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        owner = newUser();
        otherOwner = newUser();
        Link link = new Link();
        link.setOwnerId(owner);
        link.setSourceChannel("url");
        link = links.save(link);
        Application application = new Application();
        application.setOwnerId(owner);
        application.setLinkId(link.getId());
        application.setPositionKey("default");
        applicationId = applications.save(application).getId();
    }

    @Test
    void createsQuestionAndCalculatesAllCounters() throws Exception {
        String id = createQuestion("""
                {"questionText":"지원 동기","limitType":"CHARACTERS_WITH_SPACES","limitValue":1000}""");

        mvc.perform(patch("/api/v1/essay-questions/{id}", id)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"answerText":"가 A","status":"DRAFT"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterCount", is(3)))
                .andExpect(jsonPath("$.characterCountWithoutSpaces", is(2)))
                .andExpect(jsonPath("$.utf8ByteCount", is(5)))
                .andExpect(jsonPath("$.korean2ByteCount", is(4)));

        mvc.perform(get("/api/v1/applications/{id}/essay-questions", applicationId)
                        .header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].questionText", is("지원 동기")));
    }

    @Test
    void summarizesEssayProgressForTheInbox() throws Exception {
        String first = createQuestion("""
                {"questionText":"지원 동기","limitType":"NONE"}""");
        createQuestion("""
                {"questionText":"직무 역량","limitType":"NONE"}""");

        mvc.perform(patch("/api/v1/essay-questions/{id}", first)
                        .header("X-Dev-User-Id", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"answerText":"작성 완료","status":"COMPLETED"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/essay-progress").header("X-Dev-User-Id", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicationId", is(applicationId.toString())))
                .andExpect(jsonPath("$[0].totalCount", is(2)))
                .andExpect(jsonPath("$[0].completedCount", is(1)));
    }

    @Test
    void snapshotsAndRestoresAnswerHistory() throws Exception {
        String id = createQuestion("""
                {"questionText":"성장 과정","limitType":"UTF8_BYTES","limitValue":2000}""");
        mvc.perform(patch("/api/v1/essay-questions/{id}", id)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"answerText\":\"첫 번째 초안\"}"))
                .andExpect(status().isOk());
        String revisionId = JsonPath.read(mvc.perform(post("/api/v1/essay-questions/{id}/revisions", id)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"label\":\"1차 초안\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(patch("/api/v1/essay-questions/{id}", id)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"answerText\":\"최종 답변\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/essay-questions/{id}/revisions/{revisionId}/restore", id, revisionId)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerText", is("첫 번째 초안")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.revisionCount", is(1)));
    }

    @Test
    void hidesQuestionsFromAnotherOwner() throws Exception {
        String id = createQuestion("{\"questionText\":\"비공개 문항\"}");
        mvc.perform(get("/api/v1/essay-questions/{id}/revisions", id)
                        .header("X-Dev-User-Id", otherOwner))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresLimitValueWhenLimitTypeIsSelected() throws Exception {
        mvc.perform(post("/api/v1/applications/{id}/essay-questions", applicationId)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionText\":\"문항\",\"limitType\":\"UTF8_BYTES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")));
    }

    private String createQuestion(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/applications/{id}/essay-questions", applicationId)
                        .header("X-Dev-User-Id", owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private UUID newUser() {
        User user = new User();
        user.setEmail("essay+" + UUID.randomUUID() + "@example.com");
        return users.save(user).getId();
    }
}
