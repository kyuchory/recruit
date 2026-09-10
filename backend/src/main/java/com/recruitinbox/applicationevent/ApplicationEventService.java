package com.recruitinbox.applicationevent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.applicationevent.dto.CreateEventRequest;
import com.recruitinbox.applicationevent.dto.EventResponse;
import com.recruitinbox.applicationevent.dto.UpdateEventRequest;
import com.recruitinbox.common.error.ApiException;

@Service
public class ApplicationEventService {

    private final ApplicationEventRepository events;
    private final ApplicationRepository applications;

    public ApplicationEventService(ApplicationEventRepository events, ApplicationRepository applications) {
        this.events = events;
        this.applications = applications;
    }

    @Transactional
    public EventResponse create(UUID ownerId, UUID applicationId, CreateEventRequest req) {
        requireApplication(ownerId, applicationId);

        ApplicationEvent e = new ApplicationEvent();
        e.setOwnerId(ownerId);
        e.setApplicationId(applicationId);
        e.setType(req.type());
        e.setCustomLabel(blankToNull(req.customLabel()));
        e.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        e.setScheduleKind(req.scheduleKind() == null ? ScheduleKind.UNKNOWN : req.scheduleKind());
        e.setScheduledAt(req.scheduledAt());
        e.setStartAt(req.startAt());
        e.setEndAt(req.endAt());
        e.setScheduledDate(req.scheduledDate());
        if (req.timezone() != null && !req.timezone().isBlank()) {
            e.setTimezone(req.timezone().trim());
        }
        e.setLocation(blankToNull(req.location()));
        e.setUrl(blankToNull(req.url()));
        e.setNotes(req.notes() == null ? "" : req.notes());

        ScheduleShapeValidator.validate(e);
        return EventResponse.from(events.save(e));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(UUID ownerId, UUID applicationId) {
        requireApplication(ownerId, applicationId);
        return events.findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(applicationId, ownerId)
                .stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID ownerId, UUID eventId) {
        return EventResponse.from(events.findByIdAndOwnerId(eventId, ownerId)
                .orElseThrow(() -> ApiException.notFound("event")));
    }

    @Transactional
    public EventResponse update(UUID ownerId, UUID eventId, UpdateEventRequest req) {
        ApplicationEvent e = events.findByIdAndOwnerId(eventId, ownerId)
                .orElseThrow(() -> ApiException.notFound("event"));
        requireVersion(e.getVersion(), req.expectedVersion());

        String before = scheduleSignature(e);

        if (req.type() != null) {
            e.setType(req.type());
        }
        if (req.customLabel() != null) {
            e.setCustomLabel(blankToNull(req.customLabel()));
        }
        if (req.sortOrder() != null) {
            e.setSortOrder(req.sortOrder());
        }
        if (Boolean.TRUE.equals(req.clearSchedule())) {
            e.setScheduleKind(ScheduleKind.UNKNOWN);
            e.setScheduledAt(null);
            e.setStartAt(null);
            e.setEndAt(null);
            e.setScheduledDate(null);
        } else {
            if (req.scheduleKind() != null) {
                e.setScheduleKind(req.scheduleKind());
            }
            if (req.scheduledAt() != null) {
                e.setScheduledAt(req.scheduledAt());
            }
            if (req.startAt() != null) {
                e.setStartAt(req.startAt());
            }
            if (req.endAt() != null) {
                e.setEndAt(req.endAt());
            }
            if (req.scheduledDate() != null) {
                e.setScheduledDate(req.scheduledDate());
            }
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            e.setTimezone(req.timezone().trim());
        }
        if (req.location() != null) {
            e.setLocation(blankToNull(req.location()));
        }
        if (req.url() != null) {
            e.setUrl(blankToNull(req.url()));
        }
        if (req.notes() != null) {
            e.setNotes(req.notes());
        }
        if (req.result() != null) {
            e.setResult(req.result());
        }
        if (req.status() != null) {
            e.setStatus(req.status());
        }

        ScheduleShapeValidator.validate(e);

        boolean scheduleChanged = !before.equals(scheduleSignature(e));
        if (scheduleChanged) {
            // v1.1 section 6.3: schedule edits reset confirmation, bump the
            // schedule version, and (Step 15) cancel future notifications.
            e.setConfirmedAt(null);
            e.setConfirmedBy(null);
            e.setScheduleVersion(e.getScheduleVersion() + 1);
            if (e.getStatus() == EventStatus.SCHEDULED) {
                e.setStatus(EventStatus.UNSCHEDULED);
            }
            // TODO(Step 15): notificationPlanner.cancelFuture(e.getId());
        }

        try {
            return EventResponse.from(events.saveAndFlush(e));
        } catch (OptimisticLockingFailureException ex) {
            throw ApiException.versionConflict();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID eventId, long expectedVersion) {
        ApplicationEvent e = events.findByIdAndOwnerId(eventId, ownerId)
                .orElseThrow(() -> ApiException.notFound("event"));
        requireVersion(e.getVersion(), expectedVersion);
        // TODO(Step 15): cancel this event's pending notifications/deliveries first.
        events.delete(e);
    }

    private void requireApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) {
            throw ApiException.notFound("application");
        }
    }

    private static String scheduleSignature(ApplicationEvent e) {
        return String.join("|",
                String.valueOf(e.getType()),
                String.valueOf(e.getCustomLabel()),
                String.valueOf(e.getScheduleKind()),
                String.valueOf(e.getScheduledAt()),
                String.valueOf(e.getStartAt()),
                String.valueOf(e.getEndAt()),
                String.valueOf(e.getScheduledDate()),
                String.valueOf(e.getTimezone()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void requireVersion(Long actual, Long expected) {
        if (expected == null) {
            throw new ApiException(com.recruitinbox.common.error.ErrorCode.PRECONDITION_REQUIRED,
                    "expectedVersion is required");
        }
        if (!Objects.equals(expected, actual == null ? 0L : actual)) {
            throw ApiException.versionConflict();
        }
    }
}
