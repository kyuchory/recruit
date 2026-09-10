package com.recruitinbox.link;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code links} (Flyway V1, design v1.1 section 6.2) -- the shared "captured link"
 * that one or more {@code applications} (positions / re-applications) hang off.
 *
 * <h4>owner_id mapping</h4>
 * {@code owner_id} is a plain {@code UUID}, not a {@code @ManyToOne User}:
 * <ul>
 *   <li><b>Owner scoping must be explicit.</b> Every read goes through a
 *       {@code ...AndOwnerId(...)} finder. A JPA association would let code
 *       {@code link.getOwner()} navigate by id alone and silently skip the
 *       owner check.</li>
 *   <li><b>No lazy proxy / N+1.</b> The row already carries the only thing
 *       callers need (the owner id) for authorization; loading a {@code User}
 *       row is almost never required on this path.</li>
 *   <li>The DB composite FKs {@code (link_id, owner_id) -> links(id, owner_id)}
 *       on child tables still guarantee cross-owner integrity.</li>
 * </ul>
 * {@code owner_id} is {@code updatable = false}: ownership is set once from the
 * authenticated principal and never reassigned.
 */
@Entity
@Table(name = "links")
@Getter
@Setter
public class Link extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "original_url", columnDefinition = "text")
    private String originalUrl;

    @Column(name = "normalized_url", columnDefinition = "text")
    private String normalizedUrl;

    /** {@code char(64)} SHA-256 hex; NULL for text/image captures without a URL. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(name = "kind", length = 16, nullable = false)
    private String kind = "job";

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "source_channel", length = 24, nullable = false)
    private String sourceChannel;

    /** Bumped on each re-analysis; latest proposal = max(generation) run. */
    @Column(name = "extraction_generation", nullable = false)
    private long extractionGeneration = 0L;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
