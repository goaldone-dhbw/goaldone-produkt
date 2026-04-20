package de.goaldone.backend.repository;

import de.goaldone.backend.entity.LinkTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LinkTokenRepository extends JpaRepository<LinkTokenEntity, UUID> {
    void deleteByExpiresAtBefore(Instant cutoff);

    void deleteByInitiatorAccountId(UUID initiatorAccountId);
}
