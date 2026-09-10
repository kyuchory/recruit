package com.recruitinbox.link;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder is owner-scoped. Do not add a lookup that takes only an id --
 * a bare {@code findById} would return another user's link. Use
 * {@link #findByIdAndOwnerId} / {@link #requireOwned} instead.
 */
public interface LinkRepository extends JpaRepository<Link, UUID> {

    Optional<Link> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    /** URL de-dupe within one owner ({@code uq_links_owner_urlhash}). */
    Optional<Link> findByOwnerIdAndUrlHash(UUID ownerId, String urlHash);

    Page<Link> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);

    Page<Link> findByOwnerIdAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);

    /** Convenience for services: 404-style "must be owned by this user". */
    default Link requireOwned(UUID id, UUID ownerId) {
        return findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("link not found for owner"));
    }
}
