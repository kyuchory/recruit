package com.recruitinbox.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.application.dto.ConfirmRequest;
import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;
import com.recruitinbox.applicationevent.dto.EventResponse;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.parser.ExtractionRunRepository;
import com.recruitinbox.parser.ExtractionRunStatus;
import com.recruitinbox.link.LinkRepository;

/**
 * {@code POST /applications/{id}/confirm}: user-approved proposal -> confirmed
 * fields + placeholder events, in one transaction.
 *
 * <p>Guardrails (v1.1 sections 4, 6.6):
 * <ul>
 *   <li>a field whose {@code field_meta.source == "USER"} is never overwritten;</li>
 *   <li>placeholder events are created UNSCHEDULED with no notifications;</li>
 *   <li>{@code source_candidate_id} in {@code field_meta} de-dupes re-confirms.</li>
 * </ul>
 */
@Service
public class ConfirmService {

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final ExtractionRunRepository runs;
    private final LinkRepository links;

    public ConfirmService(ApplicationRepository applications, ApplicationEventRepository events,
            ExtractionRunRepository runs, LinkRepository links) {
        this.applications = applications;
        this.events = events;
        this.runs = runs;
        this.links = links;
    }

    public record Result(ApplicationResponse application, List<EventResponse> createdEvents) {
    }

    @Transactional
    public Result confirm(UUID ownerId, UUID applicationId, ConfirmRequest req) {
        Application app = applications.findByIdAndOwnerId(applicationId, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        if (req.expectedVersion() == null || !req.expectedVersion().equals(app.getVersion())) {
            throw ApiException.versionConflict();
        }

        UUID runId = req.runId();
        if (runId != null) {
            var run = runs.findByIdAndOwnerId(runId, ownerId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "extraction run not found"));
            if (!run.getLinkId().equals(app.getLinkId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "run belongs to a different link");
            }
            if (run.getStatus() != ExtractionRunStatus.SUCCEEDED
                    && run.getStatus() != ExtractionRunStatus.NEEDS_INPUT) {
                throw new ApiException(ErrorCode.RUN_IN_PROGRESS, "run has no reviewable result yet");
            }
        }

        Map<String, Object> meta = app.getFieldMeta() == null ? new HashMap<>() : new HashMap<>(app.getFieldMeta());
        Instant now = Instant.now();

        applyField(app, meta, "companyName", req.companyName(), app::getCompanyName, app::setCompanyName, runId, now);
        applyField(app, meta, "positionTitle", req.positionTitle(), app::getPositionTitle, app::setPositionTitle,
                runId, now);

        app.setFieldMeta(meta);
        app.setReviewStatus(ReviewStatus.CONFIRMED);
        applications.save(app);

        List<EventResponse> created = new ArrayList<>();
        List<ApplicationEvent> existing =
                events.findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(applicationId, ownerId);
        int nextSort = existing.stream().mapToInt(ApplicationEvent::getSortOrder).max().orElse(-1) + 1;

        if (req.placeholderCandidates() != null) {
            for (ConfirmRequest.PlaceholderCandidate c : req.placeholderCandidates()) {
                if (c.sourceCandidateId() != null && alreadyApplied(existing, c.sourceCandidateId())) {
                    continue;
                }
                ApplicationEvent e = new ApplicationEvent();
                e.setOwnerId(ownerId);
                e.setApplicationId(applicationId);
                e.setType(c.type());
                e.setCustomLabel(c.customLabel() == null || c.customLabel().isBlank() ? null : c.customLabel().trim());
                e.setScheduleKind(ScheduleKind.UNKNOWN);
                e.setStatus(EventStatus.UNSCHEDULED);
                e.setSortOrder(nextSort++);
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("source", runId == null ? "USER" : "RUN");
                em.put("runId", runId == null ? null : runId.toString());
                if (c.sourceCandidateId() != null) {
                    em.put("sourceCandidateId", c.sourceCandidateId());
                }
                e.setFieldMeta(Map.of("_provenance", em));
                created.add(EventResponse.from(events.save(e)));
            }
        }

        String sourceUrl = links.findByIdAndOwnerId(app.getLinkId(), ownerId)
                .map(com.recruitinbox.link.Link::getOriginalUrl)
                .orElse(null);
        return new Result(ApplicationResponse.from(app, sourceUrl), created);
    }

    private void applyField(Application app, Map<String, Object> meta, String key, String proposed,
            java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter,
            UUID runId, Instant now) {
        if (proposed == null) {
            return;
        }
        Object m = meta.get(key);
        if (m instanceof Map<?, ?> mm && "USER".equals(mm.get("source"))) {
            return; // user-edited: never overwrite from a proposal
        }
        setter.accept(proposed);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source", "RUN");
        entry.put("runId", runId == null ? null : runId.toString());
        entry.put("confirmedAt", now.toString());
        meta.put(key, entry);
    }

    private boolean alreadyApplied(List<ApplicationEvent> existing, String candidateId) {
        for (ApplicationEvent e : existing) {
            Object prov = e.getFieldMeta() == null ? null : e.getFieldMeta().get("_provenance");
            if (prov instanceof Map<?, ?> pm && candidateId.equals(pm.get("sourceCandidateId"))) {
                return true;
            }
        }
        return false;
    }
}
