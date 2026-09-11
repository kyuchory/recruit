package com.recruitinbox.user.dto;

import com.recruitinbox.user.User;

/** {@code GET/PATCH /settings} view (design v1.1 section 7.4). */
public record SettingsResponse(
        String email,
        boolean emailVerified,
        boolean emailEnabled,
        String timezone,
        long version) {

    public static SettingsResponse from(User u) {
        return new SettingsResponse(
                u.getEmail(),
                u.getEmailVerifiedAt() != null,
                u.isEmailEnabled(),
                u.getTimezone(),
                u.getVersion() == null ? 0L : u.getVersion());
    }
}
