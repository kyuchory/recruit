package com.recruitinbox.application.dto;

import com.recruitinbox.application.ApplicationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH: a {@code null} field means "leave unchanged" (explicit-null clearing is
 * a later refinement). {@code expectedVersion} is required (v1.1 section 7.1).
 * Allowed fields only -- URL/link/owner/source/confirmedAt are server-owned.
 */
public record UpdateApplicationRequest(
        @NotNull Long expectedVersion,
        @Size(max = 200) String companyName,
        @Size(max = 300) String positionTitle,
        @Size(max = 100) String employmentType,
        @Size(max = 100) String experience,
        String location,
        ApplicationStatus status,
        @Size(max = 10_000) String notes,
        Boolean archived) {
}
