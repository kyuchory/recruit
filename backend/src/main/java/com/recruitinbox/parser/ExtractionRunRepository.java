package com.recruitinbox.parser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Owner-scoped reads for the API. The worker's claim/recovery run through
 * {@code ExtractionRunClaimDao} (native, JdbcTemplate) in Step 9.
 */
public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, UUID> {

    Optional<ExtractionRun> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<ExtractionRun> findFirstByLinkIdAndOwnerIdOrderByGenerationDesc(UUID linkId, UUID ownerId);

    boolean existsByLinkIdAndStatusIn(UUID linkId, List<ExtractionRunStatus> statuses);
}
