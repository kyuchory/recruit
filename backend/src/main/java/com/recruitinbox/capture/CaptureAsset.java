package com.recruitinbox.capture;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code capture_assets} (Flyway V1, v1.1 section 6.2) -- a pasted body (TEXT) or
 * an uploaded image (IMAGE) attached to a link. Not analysable until READY.
 */
@Entity
@Table(name = "capture_assets")
@Getter
@Setter
public class CaptureAsset extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 8, nullable = false)
    private CaptureAssetKind kind;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;

    @Column(name = "text_content", columnDefinition = "text")
    private String textContent;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "mime", length = 100)
    private String mime;

    @Column(name = "bytes")
    private Long bytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private CaptureAssetState state = CaptureAssetState.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
