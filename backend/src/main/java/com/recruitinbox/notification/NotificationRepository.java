package com.recruitinbox.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    List<Notification> findByEventIdAndStatus(UUID eventId, NotificationStatus status);

    List<Notification> findByEventIdAndOwnerId(UUID eventId, UUID ownerId);

    Page<Notification> findByOwnerIdAndChannelAndVisibleAtIsNotNullOrderByVisibleAtDescIdDesc(
            UUID ownerId, NotificationChannel channel, Pageable pageable);

    long countByOwnerIdAndChannelAndVisibleAtIsNotNullAndReadAtIsNull(
            UUID ownerId, NotificationChannel channel);

    List<Notification> findByStatusAndScheduledSendAtLessThanEqualAndNextAttemptAtLessThanEqual(
            NotificationStatus status, Instant sendCutoff, Instant attemptCutoff);
}
