package com.recruitinbox.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder is owner-scoped (see {@link com.recruitinbox.link.LinkRepository}).
 */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Application> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);

    Page<Application> findByOwnerIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID ownerId, ApplicationStatus status, Pageable pageable);

    List<Application> findByOwnerIdAndLinkId(UUID ownerId, UUID linkId);

    /** The {@code UNIQUE(owner_id, link_id, position_key)} key. */
    Optional<Application> findByOwnerIdAndLinkIdAndPositionKey(UUID ownerId, UUID linkId, String positionKey);

    default Application requireOwned(UUID id, UUID ownerId) {
        return findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("application not found for owner"));
    }
}
