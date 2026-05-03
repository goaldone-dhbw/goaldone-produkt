package de.goaldone.backend.repository;

import de.goaldone.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link UserEntity}.
 * Manages core user identities which can be linked to multiple user accounts.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    /**
     * Finds a user by their global authentication ID.
     *
     * @param authUserId The authentication user ID.
     * @return An Optional containing the user if found.
     */
    Optional<UserEntity> findByAuthUserId(String authUserId);
}
