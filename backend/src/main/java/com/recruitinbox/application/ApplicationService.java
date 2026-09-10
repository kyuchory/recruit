package com.recruitinbox.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.application.dto.CreateApplicationRequest;
import com.recruitinbox.application.dto.UpdateApplicationRequest;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.common.web.PageResponse;
import com.recruitinbox.link.LinkRepository;

@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final LinkRepository links;

    public ApplicationService(ApplicationRepository applications, LinkRepository links) {
        this.applications = applications;
        this.links = links;
    }

    @Transactional
    public ApplicationResponse create(UUID ownerId, CreateApplicationRequest req) {
        links.findByIdAndOwnerId(req.linkId(), ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "link not found"));
        String positionKey = req.positionKey() == null || req.positionKey().isBlank()
                ? "default" : req.positionKey().trim();
        applications.findByOwnerIdAndLinkIdAndPositionKey(ownerId, req.linkId(), positionKey)
                .ifPresent(a -> {
                    throw new ApiException(ErrorCode.DUPLICATE,
                            "an application for this link and position already exists");
                });

        Application a = new Application();
        a.setOwnerId(ownerId);
        a.setLinkId(req.linkId());
        a.setPositionKey(positionKey);
        a.setCompanyName(req.companyName());
        a.setPositionTitle(req.positionTitle());
        a.setEmploymentType(req.employmentType());
        a.setExperience(req.experience());
        a.setLocation(req.location());
        a.setNotes(req.notes() == null ? "" : req.notes());
        try {
            return ApplicationResponse.from(applications.saveAndFlush(a));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE, "an application for this link and position already exists");
        }
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID ownerId, UUID id) {
        return ApplicationResponse.from(applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application")));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(UUID ownerId, ApplicationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Application> result = status == null
                ? applications.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, pageable)
                : applications.findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(ownerId, status, pageable);
        return PageResponse.of(result, ApplicationResponse::from);
    }

    @Transactional
    public ApplicationResponse update(UUID ownerId, UUID id, UpdateApplicationRequest req) {
        Application a = applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        requireVersion(a.getVersion(), req.expectedVersion());

        java.util.Map<String, Object> meta = a.getFieldMeta() == null
                ? new java.util.HashMap<>() : new java.util.HashMap<>(a.getFieldMeta());
        String userEditedAt = Instant.now().toString();

        if (req.companyName() != null) {
            a.setCompanyName(req.companyName());
            markUserEdited(meta, "companyName", userEditedAt);
        }
        if (req.positionTitle() != null) {
            a.setPositionTitle(req.positionTitle());
            markUserEdited(meta, "positionTitle", userEditedAt);
        }
        if (req.employmentType() != null) {
            a.setEmploymentType(req.employmentType());
        }
        if (req.experience() != null) {
            a.setExperience(req.experience());
        }
        if (req.location() != null) {
            a.setLocation(req.location());
        }
        if (req.notes() != null) {
            a.setNotes(req.notes());
        }
        a.setFieldMeta(meta);
        if (req.status() != null) {
            applyStatus(a, req.status());
        }
        if (Boolean.TRUE.equals(req.archived()) && a.getArchivedAt() == null) {
            a.setArchivedAt(Instant.now());
        } else if (Boolean.FALSE.equals(req.archived())) {
            a.setArchivedAt(null);
        }

        try {
            return ApplicationResponse.from(applications.saveAndFlush(a));
        } catch (OptimisticLockingFailureException e) {
            throw ApiException.versionConflict();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID id, long expectedVersion) {
        Application a = applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        requireVersion(a.getVersion(), expectedVersion);
        // FK ON DELETE CASCADE removes this application's events / rules / notifications.
        applications.delete(a);
    }

    private void applyStatus(Application a, ApplicationStatus next) {
        a.setStatus(next);
        if (next == ApplicationStatus.APPLIED && a.getAppliedAt() == null) {
            a.setAppliedAt(Instant.now());
        }
        // TODO(Step 15): status-driven notification cancellation
        //  (APPLIED/IN_PROGRESS -> cancel DOCUMENT_DEADLINE only;
        //   REJECTED/WITHDRAWN/archive -> cancel all pending).
    }

    private static void markUserEdited(java.util.Map<String, Object> meta, String field, String at) {
        meta.put(field, java.util.Map.of("source", "USER", "userEditedAt", at));
    }

    private void requireVersion(Long actual, Long expected) {
        if (expected == null) {
            throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, "expectedVersion is required");
        }
        if (!expected.equals(actual == null ? 0L : actual)) {
            throw ApiException.versionConflict();
        }
    }
}
