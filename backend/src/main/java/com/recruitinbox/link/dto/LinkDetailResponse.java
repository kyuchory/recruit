package com.recruitinbox.link.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.link.Link;
import com.recruitinbox.parser.dto.ExtractionRunResponse;

public record LinkDetailResponse(
        UUID id,
        String originalUrl,
        String normalizedUrl,
        String kind,
        String title,
        String sourceChannel,
        long extractionGeneration,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<ApplicationResponse> applications,
        ExtractionRunResponse latestRun) {

    public static LinkDetailResponse of(Link l, List<ApplicationResponse> apps, ExtractionRunResponse latestRun) {
        return new LinkDetailResponse(
                l.getId(), l.getOriginalUrl(), l.getNormalizedUrl(), l.getKind(), l.getTitle(),
                l.getSourceChannel(), l.getExtractionGeneration(), l.getArchivedAt(),
                l.getVersion() == null ? 0L : l.getVersion(), l.getCreatedAt(), l.getUpdatedAt(),
                apps, latestRun);
    }
}
