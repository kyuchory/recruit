package com.recruitinbox.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}

    public record CreateProfileItemRequest(
            @NotNull ProfileCategory category,
            @Size(max = 200) String label,
            Map<String, @Size(max = 20_000) String> fields,
            @Size(max = 20_000) String valueText,
            @Size(max = 100_000) String details,
            LocalDate startedOn,
            LocalDate endedOn,
            Boolean sensitive) {}

    public record UpdateProfileItemRequest(
            @NotNull Long expectedVersion,
            ProfileCategory category,
            @Size(max = 200) String label,
            Map<String, @Size(max = 20_000) String> fields,
            @Size(max = 20_000) String valueText,
            @Size(max = 100_000) String details,
            LocalDate startedOn,
            LocalDate endedOn,
            Boolean clearDates,
            Boolean sensitive) {}

    public record ProfileItemResponse(
            UUID id,
            ProfileCategory category,
            String label,
            Map<String, String> fields,
            String valueText,
            String details,
            LocalDate startedOn,
            LocalDate endedOn,
            boolean sensitive,
            int sortOrder,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        static ProfileItemResponse from(CareerProfileItem item, Map<String, String> fields, String valueText, String details) {
            return new ProfileItemResponse(item.getId(), item.getCategory(), item.getLabel(), fields, valueText,
                    details, item.getStartedOn(), item.getEndedOn(), item.isSensitive(),
                    item.getSortOrder(), item.getVersion(), item.getCreatedAt(), item.getUpdatedAt());
        }
    }
}
