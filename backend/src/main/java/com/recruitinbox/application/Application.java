package com.recruitinbox.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code applications} (Flyway V1, design v1.1 section 6.3) -- one person's
 * application for one position on a {@code links} row.
 *
 * <p>{@code owner_id} and {@code link_id} are plain {@code UUID}s (rationale on
 * {@link com.recruitinbox.link.Link}). {@code link_id} in particular is never a
 * {@code @ManyToOne}: navigating to the parent link must re-assert the owner, so
 * callers load it via {@code LinkRepository.findByIdAndOwnerId}.
 *
 * <p>DB guards not re-expressed in JPA: {@code UNIQUE(owner_id, link_id, position_key)},
 * {@code (link_id, owner_id)} composite FK, {@code review_status='CONFIRMED'} needing
 * company/position (there is no such CHECK in V1 -- see report notes).
 */
@Entity
@Table(
        name = "applications",
        uniqueConstraints = @UniqueConstraint(
                name = "applications_owner_id_link_id_position_key_key",
                columnNames = {"owner_id", "link_id", "position_key"}))
@Getter
@Setter
public class Application extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    /** Distinguishes positions and re-application rounds under the same link. */
    @Column(name = "position_key", length = 100, nullable = false)
    private String positionKey;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "position_title", length = 300)
    private String positionTitle;

    @Column(name = "employment_type", length = 100)
    private String employmentType;

    @Column(name = "experience", length = 100)
    private String experience;

    @Column(name = "location", columnDefinition = "text")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ApplicationStatus status = ApplicationStatus.INTERESTED;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "notes", columnDefinition = "text", nullable = false)
    private String notes = "";

    /**
     * {@code jsonb} per-field provenance (source / run_id / evidence_ref /
     * confirmed_at / user_edited_at -- v1.1 section 6.3 example).
     *
     * <p>Mapped as {@code Map<String,Object>} via Hibernate's native
     * {@code SqlTypes.JSON} (Jackson under the hood), not a raw {@code String},
     * because the server both writes structured entries and reads individual
     * keys ("was this field user-edited?") when deciding whether AI proposals may
     * touch it. A String field would force every caller to parse/serialise and
     * invites storing malformed JSON. Kept as an untyped map for now; a typed
     * {@code FieldMeta} record can replace it without a schema change.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_meta", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fieldMeta = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
