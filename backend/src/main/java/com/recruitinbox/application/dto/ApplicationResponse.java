package com.recruitinbox.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationStatus;
import com.recruitinbox.application.ReviewStatus;

public record ApplicationResponse(
        UUID id,
        UUID linkId,
        String positionKey,
        String companyName,
        String positionTitle,
        String employmentType,
        String experience,
        String location,
        ApplicationStatus status,
        Instant appliedAt,
        String notes,
        Map<String, Object> fieldMeta,
        ReviewStatus reviewStatus,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(
                a.getId(), a.getLinkId(), a.getPositionKey(),
                a.getCompanyName(), a.getPositionTitle(), a.getEmploymentType(), a.getExperience(), a.getLocation(),
                a.getStatus(), a.getAppliedAt(), a.getNotes(),
                a.getFieldMeta(), a.getReviewStatus(), a.getArchivedAt(),
                a.getVersion() == null ? 0L : a.getVersion(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
