package com.recruitinbox.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareerProfileRepository extends JpaRepository<CareerProfileItem, UUID> {
    List<CareerProfileItem> findByOwnerIdOrderByCategoryAscSortOrderAscIdAsc(UUID ownerId);
    List<CareerProfileItem> findByOwnerIdAndCategoryOrderBySortOrderAscIdAsc(UUID ownerId, ProfileCategory category);
    Optional<CareerProfileItem> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select coalesce(max(i.sortOrder), -1) + 1 from CareerProfileItem i where i.ownerId = :ownerId and i.category = :category")
    int nextSortOrder(@Param("ownerId") UUID ownerId, @Param("category") ProfileCategory category);
}
