package com.recruitinbox.link.dto;

import java.util.UUID;

/**
 * {@code 202} for a new import, {@code 200} when the same owner already saved
 * this URL ({@code extractionRunId} may be null for the dedupe case).
 */
public record LinkImportResponse(
        UUID linkId,
        UUID applicationId,
        UUID extractionRunId,
        String status,
        boolean duplicate) {
}
