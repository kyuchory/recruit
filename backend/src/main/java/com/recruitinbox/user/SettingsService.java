package com.recruitinbox.user;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.notification.NotificationChannel;
import com.recruitinbox.notification.NotificationPlanner;
import com.recruitinbox.user.dto.SettingsResponse;
import com.recruitinbox.user.dto.UpdateSettingsRequest;

/**
 * {@code GET/PATCH /settings} (design v1.1 section 7.4). Timezone is validated as
 * an IANA {@link ZoneId} and never propagated to existing event schedules.
 * Turning the email channel off cancels that owner's pending EMAIL notifications
 * immediately.
 */
@Service
public class SettingsService {

    private final UserRepository users;
    private final NotificationPlanner notificationPlanner;

    public SettingsService(UserRepository users, NotificationPlanner notificationPlanner) {
        this.users = users;
        this.notificationPlanner = notificationPlanner;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(UUID userId) {
        return SettingsResponse.from(require(userId));
    }

    @Transactional
    public SettingsResponse update(UUID userId, UpdateSettingsRequest req) {
        User u = require(userId);
        requireVersion(u.getVersion(), req.expectedVersion());

        if (req.timezone() != null && !req.timezone().isBlank()) {
            String tz = req.timezone().trim();
            try {
                ZoneId.of(tz);
            } catch (RuntimeException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "invalid timezone",
                        Map.of("timezone", "must be an IANA zone id, e.g. Asia/Seoul"));
            }
            u.setTimezone(tz);
        }

        boolean emailDisabled = false;
        if (req.emailEnabled() != null) {
            if (req.emailEnabled() && !u.isEmailEnabled()) {
                u.setEmailEnabled(true);
                if (u.getEmailConsentAt() == null) {
                    u.setEmailConsentAt(Instant.now());
                }
            } else if (!req.emailEnabled() && u.isEmailEnabled()) {
                u.setEmailEnabled(false);
                emailDisabled = true;
            }
        }

        try {
            User saved = users.saveAndFlush(u);
            if (emailDisabled) {
                notificationPlanner.cancelPendingChannelForOwner(userId, NotificationChannel.EMAIL);
            }
            return SettingsResponse.from(saved);
        } catch (OptimisticLockingFailureException e) {
            throw ApiException.versionConflict();
        }
    }

    private User require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("user"));
    }

    private void requireVersion(Long actual, Long expected) {
        if (expected == null) {
            throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, "expectedVersion is required");
        }
        if (!expected.equals(actual == null ? 0L : actual)) {
            throw ApiException.versionConflict();
        }
    }
}
