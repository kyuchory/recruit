package com.recruitinbox.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code users} is the account root, so lookups are by id or by the natural
 * key (email). All <em>owned</em> data is queried via owner-scoped repositories.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Matches the {@code lower(email)} partial unique index. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
