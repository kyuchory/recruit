package com.recruitinbox.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Owner-scoped only. */
public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    Optional<NotificationRule> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<NotificationRule> findByEventIdAndOwnerId(UUID eventId, UUID ownerId);

    List<NotificationRule> findByEventIdAndOwnerIdAndEnabledTrue(UUID eventId, UUID ownerId);
}
