package com.recruitinbox.essay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EssayRevisionRepository extends JpaRepository<EssayRevision, UUID> {
    List<EssayRevision> findByQuestionIdAndOwnerIdOrderByRevisionNoDesc(UUID questionId, UUID ownerId);
    Optional<EssayRevision> findByIdAndQuestionIdAndOwnerId(UUID id, UUID questionId, UUID ownerId);
    long countByQuestionIdAndOwnerId(UUID questionId, UUID ownerId);

    @Query("select coalesce(max(r.revisionNo), 0) + 1 from EssayRevision r where r.questionId = :questionId and r.ownerId = :ownerId")
    int nextRevisionNo(@Param("questionId") UUID questionId, @Param("ownerId") UUID ownerId);
}
