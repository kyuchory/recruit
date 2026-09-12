package com.recruitinbox.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.application.dto.CreateApplicationRequest;
import com.recruitinbox.application.dto.ManualCreateApplicationRequest;
import com.recruitinbox.application.dto.UpdateApplicationRequest;
import com.recruitinbox.applicationevent.ApplicationEventType;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.common.web.PageResponse;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.link.UrlNormalizer;
import com.recruitinbox.notification.NotificationPlanner;

@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final LinkRepository links;
    private final NotificationPlanner notificationPlanner;

    public ApplicationService(ApplicationRepository applications, LinkRepository links,
            NotificationPlanner notificationPlanner) {
        this.applications = applications;
        this.links = links;
        this.notificationPlanner = notificationPlanner;
    }

    @Transactional
    public ApplicationResponse create(UUID ownerId, CreateApplicationRequest req) {
        Link link = links.findByIdAndOwnerId(req.linkId(), ownerId)
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
            return ApplicationResponse.from(applications.saveAndFlush(a), link.getOriginalUrl());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE, "an application for this link and position already exists");
        }
    }

    @Transactional
    public ApplicationResponse createManual(UUID ownerId, ManualCreateApplicationRequest req) {
        String companyName = req.companyName().trim();
        String positionTitle = req.positionTitle().trim();

        Link link = new Link();
        link.setOwnerId(ownerId);
        link.setTitle(companyName + " / " + positionTitle);
        applySourceUrl(ownerId, link, req.sourceUrl());
        if (link.getOriginalUrl() == null) {
            link.setSourceChannel("manual");
        }
        link = links.save(link);

        Application application = new Application();
        application.setOwnerId(ownerId);
        application.setLinkId(link.getId());
        application.setPositionKey("default");
        application.setCompanyName(companyName);
        application.setPositionTitle(positionTitle);
        application.setNotes(req.notes() == null ? "" : req.notes().trim());
        application.setReviewStatus(ReviewStatus.NOT_REQUIRED);

        String userEditedAt = Instant.now().toString();
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        markUserEdited(meta, "companyName", userEditedAt);
        markUserEdited(meta, "positionTitle", userEditedAt);
        application.setFieldMeta(meta);

        return ApplicationResponse.from(applications.saveAndFlush(application), link.getOriginalUrl());
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID ownerId, UUID id) {
        Application application = applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        Link link = requireLink(ownerId, application.getLinkId());
        return ApplicationResponse.from(application, link.getOriginalUrl());
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> list(UUID ownerId, ApplicationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Application> result = status == null
                ? applications.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, pageable)
                : applications.findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(ownerId, status, pageable);
        Set<UUID> linkIds = result.getContent().stream().map(Application::getLinkId).collect(Collectors.toSet());
        Map<UUID, Link> linksById = links.findByOwnerIdAndIdIn(ownerId, linkIds).stream()
                .collect(Collectors.toMap(Link::getId, Function.identity()));
        return PageResponse.of(result, application -> {
            Link link = linksById.get(application.getLinkId());
            return ApplicationResponse.from(application, link == null ? null : link.getOriginalUrl());
        });
    }

    @Transactional
    public ApplicationResponse update(UUID ownerId, UUID id, UpdateApplicationRequest req) {
        Application a = applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        requireVersion(a.getVersion(), req.expectedVersion());
        Link link = requireLink(ownerId, a.getLinkId());

        java.util.Map<String, Object> meta = a.getFieldMeta() == null
                ? new java.util.HashMap<>() : new java.util.HashMap<>(a.getFieldMeta());
        String userEditedAt = Instant.now().toString();

        if (req.sourceUrl() != null) {
            applySourceUrl(ownerId, link, req.sourceUrl());
            markUserEdited(meta, "sourceUrl", userEditedAt);
        }

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
            applyStatus(ownerId, a, req.status());
        }
        if (Boolean.TRUE.equals(req.archived()) && a.getArchivedAt() == null) {
            a.setArchivedAt(Instant.now());
            notificationPlanner.cancelAllForApplication(ownerId, a.getId());
        } else if (Boolean.FALSE.equals(req.archived()) && a.getArchivedAt() != null) {
            a.setArchivedAt(null);
            notificationPlanner.reactivateForApplication(ownerId, a.getId());
        }

        try {
            links.saveAndFlush(link);
            return ApplicationResponse.from(applications.saveAndFlush(a), link.getOriginalUrl());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.DUPLICATE, "this URL is already used by another saved link");
        } catch (OptimisticLockingFailureException e) {
            throw ApiException.versionConflict();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID id, long expectedVersion) {
        Application a = applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("application"));
        requireVersion(a.getVersion(), expectedVersion);
        // FK ON DELETE CASCADE removes this application's events / rules / notifications,
        // so every pending notification is stopped (v1.1 section 9.1: delete cancels all).
        applications.delete(a);
    }

    /** Status-driven notification cancellation, v1.1 section 9.1. */
    private void applyStatus(UUID ownerId, Application a, ApplicationStatus next) {
        a.setStatus(next);
        if (next == ApplicationStatus.APPLIED && a.getAppliedAt() == null) {
            a.setAppliedAt(Instant.now());
        }
        switch (next) {
            case APPLIED, IN_PROGRESS -> notificationPlanner.cancelForApplicationEventTypes(
                    ownerId, a.getId(), java.util.Set.of(ApplicationEventType.DOCUMENT_DEADLINE));
            case REJECTED, WITHDRAWN -> notificationPlanner.cancelAllForApplication(ownerId, a.getId());
            // INTERESTED / PLANNED / ACCEPTED keep their schedules (ACCEPTED still gets ORIENTATION).
            default -> { }
        }
    }

    private static void markUserEdited(java.util.Map<String, Object> meta, String field, String at) {
        meta.put(field, java.util.Map.of("source", "USER", "userEditedAt", at));
    }

    private Link requireLink(UUID ownerId, UUID linkId) {
        return links.findByIdAndOwnerId(linkId, ownerId)
                .orElseThrow(() -> ApiException.notFound("link"));
    }

    private void applySourceUrl(UUID ownerId, Link link, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        UrlNormalizer.Normalized normalized = UrlNormalizer.normalize(rawUrl);
        links.findByOwnerIdAndUrlHash(ownerId, normalized.urlHash())
                .filter(existing -> !existing.getId().equals(link.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE, "this URL is already used by another saved link");
                });
        link.setOriginalUrl(normalized.original());
        link.setNormalizedUrl(normalized.normalized());
        link.setUrlHash(normalized.urlHash());
        link.setSourceChannel("url");
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
