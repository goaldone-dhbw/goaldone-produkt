package de.goaldone.backend.repository;

import de.goaldone.backend.entity.LinkTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository interface for {@link LinkTokenEntity}.
 * Manages link tokens used for various authentication or linking processes.
 */
@Repository
public interface LinkTokenRepository extends JpaRepository<LinkTokenEntity, UUID> {
    /**
     * Deletes all tokens that have expired before the given cutoff time.
     *
     * @param cutoff The instant before which tokens are considered expired.
     */
    void deleteByExpiresAtBefore(Instant cutoff);
}
