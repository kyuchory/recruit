package com.recruitinbox.user;

import java.time.Instant;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code users} (Flyway V1, design v1.1 section 6.2).
 *
 * <p>The account root. Every owned entity references it only by
 * {@code owner_id UUID}, never by a JPA association -- see {@code Link}/{@code Application}.
 *
 * <p>Not modelled here: the partial unique index on {@code lower(email)}
 * (DB-only; enforced by {@code uq_users_email_lower}).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone = "Asia/Seoul";

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "email_consent_at")
    private Instant emailConsentAt;

    @Column(name = "email_suppressed_at")
    private Instant emailSuppressedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private UserState state = UserState.ACTIVE;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;
}
