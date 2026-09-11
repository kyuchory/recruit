package com.recruitinbox.applicationevent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder is owner-scoped. Listing an application's events also passes
 * {@code ownerId} so a wrong {@code applicationId} cannot leak another user's
 * timeline.
 */
public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {

    Optional<ApplicationEvent> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    List<ApplicationEvent> findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(
            UUID applicationId, UUID ownerId);

    long countByApplicationIdAndOwnerId(UUID applicationId, UUID ownerId);

    /** Calendar view: exact-time events in a window (matches idx_events_owner_start). */
    List<ApplicationEvent> findByOwnerIdAndStatusAndStartAtBetween(
            UUID ownerId, EventStatus status, Instant fromInclusive, Instant toExclusive);

    List<ApplicationEvent> findByOwnerIdAndTypeAndApplicationId(
            UUID ownerId, ApplicationEventType type, UUID applicationId);

    List<ApplicationEvent> findByOwnerIdAndStatus(UUID ownerId, EventStatus status);

    default ApplicationEvent requireOwned(UUID id, UUID ownerId) {
        return findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("event not found for owner"));
    }
}
