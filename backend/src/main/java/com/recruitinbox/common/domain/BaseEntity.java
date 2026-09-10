package com.recruitinbox.common.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * Shared identity/audit columns for every business table
 * (Flyway V1 convention: {@code id uuid PK}, {@code version bigint},
 * {@code created_at / updated_at timestamptz DEFAULT now()}).
 *
 * <p>{@code id} is generated in the application (JPA {@code GenerationType.UUID});
 * the DB {@code DEFAULT gen_random_uuid()} stays as a fallback for non-JPA inserts.
 *
 * <p>{@code version} is JPA-managed optimistic locking. Hibernate seeds a numeric
 * {@code @Version} at 0 on first insert, so Flyway V2 relaxes the V1
 * {@code CHECK (version > 0)} to {@code >= 0} on the mapped tables.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isPersisted() {
        return id != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Constant per type: keeps hashCode stable before/after id assignment.
        return getClass().hashCode();
    }
}
