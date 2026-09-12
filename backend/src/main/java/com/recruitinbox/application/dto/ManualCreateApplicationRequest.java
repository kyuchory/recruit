package com.recruitinbox.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates a user-entered application without requiring a source URL. */
public record ManualCreateApplicationRequest(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 300) String positionTitle,
        @Size(max = 4096) String sourceUrl,
        @Size(max = 10_000) String notes) {
}
