package com.recruitinbox.common.security;

import java.util.UUID;

/**
 * The one place the rest of the app asks "who is calling?". Controllers never
 * read headers or the security context directly -- they take the id from here.
 *
 * <p>Dev: {@link DevCurrentUserProvider} (X-Dev-User-Id header or a seeded user).
 * Prod: a session-backed implementation added with Google OAuth2 login.
 */
public interface CurrentUserProvider {

    /** @throws com.recruitinbox.common.error.ApiException UNAUTHENTICATED if there is no caller */
    UUID requireCurrentUserId();
}
