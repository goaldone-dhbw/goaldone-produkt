package de.goaldone.backend.repository;

import de.goaldone.backend.entity.UserIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for {@link UserIdentityEntity}.
 * Manages core user identities which can be linked to multiple user accounts.
 */
@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
}
