package com.recruitinbox.common.security;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.user.UserRepository;

/**
 * Non-prod caller resolution:
 * <ul>
 *   <li>{@code X-Dev-User-Id: <uuid>} header -> that user (must exist), or</li>
 *   <li>no header -> the seeded dev user {@link DevUserInitializer#DEV_USER_ID}.</li>
 * </ul>
 * Disabled under the {@code prod} profile so a header can never stand in for a
 * real session in production.
 */
@Component
@Profile("!prod")
public class DevCurrentUserProvider implements CurrentUserProvider {

    static final String HEADER = "X-Dev-User-Id";

    private final UserRepository users;

    public DevCurrentUserProvider(UserRepository users) {
        this.users = users;
    }

    @Override
    public UUID requireCurrentUserId() {
        UUID id = fromHeader();
        if (id == null) {
            id = DevUserInitializer.DEV_USER_ID;
        }
        if (!users.existsById(id)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "unknown dev user: " + id);
        }
        return id;
    }

    private UUID fromHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        String raw = attrs.getRequest().getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "malformed " + HEADER);
        }
    }
}
