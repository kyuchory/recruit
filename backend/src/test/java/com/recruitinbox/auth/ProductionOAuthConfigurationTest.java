package com.recruitinbox.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.support.AbstractIntegrationTest;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionOAuthConfigurationTest extends AbstractIntegrationTest {

    @Autowired
    private ClientRegistrationRepository registrations;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private MockMvc mvc;

    @Test
    void prodProfileCreatesGoogleOAuthAndSessionAuthentication() {
        var google = registrations.findByRegistrationId("google");

        assertThat(google).isNotNull();
        assertThat(google.getClientId()).isEqualTo("test-google-client");
        assertThat(currentUserProvider).isInstanceOf(SessionCurrentUserProvider.class);
    }

    @Test
    void prodProfileRejectsApiRequestsWithoutAnOAuthSession() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHENTICATED")));
    }

    @Test
    void googleAuthorizationEndpointIsEnabled() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://accounts.google.com/");
    }
}
