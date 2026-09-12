package com.recruitinbox.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void usesRegistrationProviderAndRestoresInternalReturnPath() throws Exception {
        UserIdentityService identities = mock(UserIdentityService.class);
        OAuth2User principal = mock(OAuth2User.class);
        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        UUID userId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("kakao");
        when(principal.getName()).thenReturn("kakao-subject");
        when(principal.getAttribute("email")).thenReturn("user@example.com");
        when(identities.upsertFromOidc("KAKAO", "kakao-subject", "user@example.com")).thenReturn(userId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(AuthFlowController.RETURN_TO_SESSION_ATTRIBUTE, "/app?resume=url");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OAuth2LoginSuccessHandler(identities, "http://localhost:3000/app")
                .onAuthenticationSuccess(request, response, authentication);

        verify(identities).upsertFromOidc("KAKAO", "kakao-subject", "user@example.com");
        assertThat(request.getSession().getAttribute(SessionCurrentUserProvider.SESSION_UID)).isEqualTo(userId);
        assertThat(request.getSession().getAttribute(AuthFlowController.RETURN_TO_SESSION_ATTRIBUTE)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/app?resume=url");
    }
}
