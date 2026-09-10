package com.recruitinbox.auth;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.common.security.CurrentUserProvider;

/**
 * Session-backed caller resolution. Active only when Google OAuth2 is configured
 * (a {@link ClientRegistrationRepository} bean exists); then it is {@code @Primary}
 * over {@link com.recruitinbox.common.security.DevCurrentUserProvider}.
 *
 * <p>The internal user id is stamped on the HTTP session by
 * {@link OAuth2LoginSuccessHandler}.
 */
@Component
@Primary
@ConditionalOnBean(ClientRegistrationRepository.class)
public class SessionCurrentUserProvider implements CurrentUserProvider {

    public static final String SESSION_UID = "recruitInbox.uid";

    @Override
    public UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "login required");
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "no request context");
        }
        var session = attrs.getRequest().getSession(false);
        Object uid = session == null ? null : session.getAttribute(SESSION_UID);
        if (uid instanceof UUID u) {
            return u;
        }
        if (uid instanceof String s) {
            return UUID.fromString(s);
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED, "session has no user id");
    }
}
