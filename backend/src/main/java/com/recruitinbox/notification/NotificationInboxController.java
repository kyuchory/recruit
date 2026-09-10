package com.recruitinbox.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.security.CurrentUserProvider;

@RestController
@RequestMapping("/api/v1")
public class NotificationInboxController {

    private final NotificationRepository notifications;
    private final CurrentUserProvider currentUser;

    public NotificationInboxController(NotificationRepository notifications, CurrentUserProvider currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    public record InboxItem(UUID id, UUID eventId, Instant scheduledSendAt, Instant visibleAt, Instant readAt,
            String deliveryStatus, Object payload) {
    }

    public record InboxPage(List<InboxItem> items, long unreadCount, int page, int size, long totalElements) {
    }

    /** Public IN_APP notifications only (v1.1 section 7.2). */
    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public InboxPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID owner = currentUser.requireCurrentUserId();
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        var result = notifications
                .findByOwnerIdAndChannelAndVisibleAtIsNotNullOrderByVisibleAtDescIdDesc(
                        owner, NotificationChannel.IN_APP, pageable);
        long unread = notifications
                .countByOwnerIdAndChannelAndVisibleAtIsNotNullAndReadAtIsNull(owner, NotificationChannel.IN_APP);
        var items = result.getContent().stream()
                .map(n -> new InboxItem(n.getId(), n.getEventId(), n.getScheduledSendAt(), n.getVisibleAt(),
                        n.getReadAt(), friendly(n.getStatus()), n.getPayload()))
                .toList();
        return new InboxPage(items, unread, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public record MarkReadRequest(Boolean read) {
    }

    @PatchMapping("/notifications/{id}")
    @Transactional
    public InboxItem markRead(@PathVariable UUID id, @RequestBody MarkReadRequest req) {
        UUID owner = currentUser.requireCurrentUserId();
        Notification n = notifications.findByIdAndOwnerId(id, owner)
                .orElseThrow(() -> ApiException.notFound("notification"));
        if (Boolean.TRUE.equals(req.read()) && n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
        return new InboxItem(n.getId(), n.getEventId(), n.getScheduledSendAt(), n.getVisibleAt(), n.getReadAt(),
                friendly(n.getStatus()), n.getPayload());
    }

    @GetMapping("/events/{eventId}/notifications")
    @Transactional(readOnly = true)
    public List<InboxItem> forEvent(@PathVariable UUID eventId) {
        UUID owner = currentUser.requireCurrentUserId();
        return notifications.findByEventIdAndOwnerId(eventId, owner).stream()
                .map(n -> new InboxItem(n.getId(), n.getEventId(), n.getScheduledSendAt(), n.getVisibleAt(),
                        n.getReadAt(), friendly(n.getStatus()), n.getPayload()))
                .toList();
    }

    /** Provider acceptance is not "delivered" -- keep the wording modest (v1.1 section 9.4). */
    private String friendly(NotificationStatus s) {
        return switch (s) {
            case PENDING -> "SCHEDULED";
            case DISPATCHING -> "PROCESSING";
            case COMPLETED -> "SENT";
            case PARTIAL_FAILED -> "PARTIAL";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
        };
    }
}
