package com.recruitinbox.auth;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * On successful OIDC login: upsert {@code users}/{@code auth_identities},
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
        String provider = authentication instanceof OAuth2AuthenticationToken token
                ? token.getAuthorizedClientRegistrationId().toUpperCase()
                : "GOOGLE";
        UUID userId = identities.upsertFromOidc(provider, subject, email);
        var session = request.getSession(true);
        session.setAttribute(SessionCurrentUserProvider.SESSION_UID, userId);

        Object requestedPath = session.getAttribute(AuthFlowController.RETURN_TO_SESSION_ATTRIBUTE);
        session.removeAttribute(AuthFlowController.RETURN_TO_SESSION_ATTRIBUTE);
        if (requestedPath instanceof String path && !"/app".equals(path)) {
            URI base = URI.create(postLoginUrl);
            String origin = base.getScheme() + "://" + base.getAuthority();
            response.sendRedirect(origin + AuthFlowController.safeReturnTo(path));
            return;
        }
        response.sendRedirect(postLoginUrl);
    }
}
