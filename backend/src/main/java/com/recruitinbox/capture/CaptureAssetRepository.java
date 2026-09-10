package com.recruitinbox.capture;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Owner-scoped only. */
public interface CaptureAssetRepository extends JpaRepository<CaptureAsset, UUID> {

    Optional<CaptureAsset> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<CaptureAsset> findByLinkIdAndOwnerId(UUID linkId, UUID ownerId);

    List<CaptureAsset> findByLinkIdAndOwnerIdAndState(UUID linkId, UUID ownerId, CaptureAssetState state);
}
