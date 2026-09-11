package com.recruitinbox.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH /settings: {@code null} means "leave unchanged". {@code expectedVersion}
 * is required (v1.1 section 7.1). Schedule timezones are never bulk-rewritten.
 */
public record UpdateSettingsRequest(
        @NotNull Long expectedVersion,
        @Size(max = 64) String timezone,
        Boolean emailEnabled) {
}
