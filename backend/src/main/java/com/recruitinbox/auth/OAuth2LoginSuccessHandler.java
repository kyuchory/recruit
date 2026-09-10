package com.recruitinbox.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * On successful Google login: upsert {@code users}/{@code auth_identities},
 * stamp the internal id on the session, and redirect to the SPA.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserIdentityService identities;
    private final String postLoginUrl;

    public OAuth2LoginSuccessHandler(UserIdentityService identities,
            @Value("${app.auth.frontend-post-login-url}") String postLoginUrl) {
        this.identities = identities;
        this.postLoginUrl = postLoginUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String subject = principal.getName(); // OIDC "sub"
        String email = principal.getAttribute("email");
        UUID userId = identities.upsertFromOidc("GOOGLE", subject, email);
        request.getSession(true).setAttribute(SessionCurrentUserProvider.SESSION_UID, userId);
        response.sendRedirect(postLoginUrl);
    }
}
