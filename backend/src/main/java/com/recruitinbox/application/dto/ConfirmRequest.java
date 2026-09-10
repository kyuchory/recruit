package com.recruitinbox.application.dto;

import java.util.List;
import java.util.UUID;

import com.recruitinbox.applicationevent.ApplicationEventType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Applies a reviewed extraction proposal to the application (v1.1 sections 6.6,
 * 8.5). Only the fields listed here are touched; a field the user already edited
 * is never overwritten. Placeholder candidates become UNSCHEDULED events -- no
 * schedule, no notifications.
 */
public record ConfirmRequest(
        @NotNull Long expectedVersion,
        UUID runId,
        @Size(max = 200) String companyName,
        @Size(max = 300) String positionTitle,
        List<PlaceholderCandidate> placeholderCandidates) {

    public record PlaceholderCandidate(
            @NotNull ApplicationEventType type,
            @Size(max = 200) String customLabel,
            String sourceCandidateId) {
    }
}
