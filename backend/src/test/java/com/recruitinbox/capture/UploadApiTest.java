package com.recruitinbox.capture;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

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
class UploadApiTest {

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
        ownerA = user();
        ownerB = user();
        Link l = new Link();
        l.setOwnerId(ownerA);
        l.setSourceChannel("url");
        linkA = links.save(l).getId();
    }

    private UUID user() {
        User u = new User();
        u.setEmail("up+" + UUID.randomUUID() + "@example.com");
        return users.save(u).getId();
    }

    private byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void createUploadPutImageThenReadItBack() throws Exception {
        String created = mvc.perform(post("/api/v1/uploads")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkId\":\"" + linkA + "\",\"mime\":\"image/png\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadMethod", is("POST")))
                .andExpect(jsonPath("$.uploadUrl", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String assetId = com.jayway.jsonpath.JsonPath.read(created, "$.assetId");

        mvc.perform(multipart("/api/v1/uploads/{id}/content", assetId)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "j.png", "image/png", png()))
                        .header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("READY")))
                .andExpect(jsonPath("$.width", is(4)));

        mvc.perform(get("/api/v1/uploads/{id}", assetId).header("X-Dev-User-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("READY")))
                .andExpect(jsonPath("$.readUrl", notNullValue()));

        mvc.perform(get("/api/v1/uploads/{id}", assetId).header("X-Dev-User-Id", ownerB))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonImageBody() throws Exception {
        String created = mvc.perform(post("/api/v1/uploads")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkId\":\"" + linkA + "\",\"mime\":\"image/png\"}"))
                .andReturn().getResponse().getContentAsString();
        String assetId = com.jayway.jsonpath.JsonPath.read(created, "$.assetId");

        mvc.perform(multipart("/api/v1/uploads/{id}/content", assetId)
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "x.svg", "image/svg+xml",
                                "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes()))
                        .header("X-Dev-User-Id", ownerA))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code", is("UNSUPPORTED_MEDIA")));
    }

    @Test
    void rejectsUnsupportedDeclaredMime() throws Exception {
        mvc.perform(post("/api/v1/uploads")
                        .header("X-Dev-User-Id", ownerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkId\":\"" + linkA + "\",\"mime\":\"image/gif\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
