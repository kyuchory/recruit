package com.recruitinbox.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * v1.1 has no standalone "create application" -- applications are born from a
 * link ({@code POST /links} or {@code POST /links/:id/applications}). This
 * endpoint keeps that invariant by requiring an owned {@code linkId}; the URL
 * import flow (Step 8) is the primary creator.
 */
public record CreateApplicationRequest(
        @NotNull UUID linkId,
        @Size(max = 100) String positionKey,
        @Size(max = 200) String companyName,
        @Size(max = 300) String positionTitle,
        @Size(max = 100) String employmentType,
        @Size(max = 100) String experience,
        String location,
        @Size(max = 10_000) String notes) {
}
