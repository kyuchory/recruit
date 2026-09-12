package com.recruitinbox.essay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EssayQuestionRepository extends JpaRepository<EssayQuestion, UUID> {
    interface ProgressRow {
        UUID getApplicationId();
        long getTotalCount();
        long getCompletedCount();
    }

    List<EssayQuestion> findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(UUID applicationId, UUID ownerId);
    Optional<EssayQuestion> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select coalesce(max(q.sortOrder), -1) + 1 from EssayQuestion q where q.applicationId = :applicationId and q.ownerId = :ownerId")
    int nextSortOrder(@Param("applicationId") UUID applicationId, @Param("ownerId") UUID ownerId);

    @Query(value = """
            SELECT application_id AS "applicationId",
                   count(*) AS "totalCount",
                   count(*) FILTER (WHERE status = 'COMPLETED') AS "completedCount"
            FROM application_essay_questions
            WHERE owner_id = :ownerId
            GROUP BY application_id
            """, nativeQuery = true)
    List<ProgressRow> findProgressByOwnerId(@Param("ownerId") UUID ownerId);
}
