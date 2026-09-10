package com.recruitinbox.parser.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.recruitinbox.parser.ExtractionRun;
import com.recruitinbox.parser.ExtractionRunStatus;

/** Owner-facing run view; excludes lease tokens and raw fetch secrets. */
public record ExtractionRunResponse(
        UUID id,
        UUID linkId,
        long generation,
        String sourceKind,
        ExtractionRunStatus status,
        String progressStage,
        int attemptCount,
        Map<String, Object> result,
        List<Object> warnings,
        String errorCode,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ExtractionRunResponse from(ExtractionRun r) {
        return new ExtractionRunResponse(
                r.getId(), r.getLinkId(), r.getGeneration(), r.getSourceKind(), r.getStatus(),
                r.getProgressStage(), r.getAttemptCount(), r.getResult(), r.getWarnings(),
                r.getErrorCode(), r.getStartedAt(), r.getFinishedAt(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
