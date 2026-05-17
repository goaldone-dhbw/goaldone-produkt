package de.goaldone.backend.repository;

import de.goaldone.backend.entity.SchedulePlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchedulePlanRepository extends JpaRepository<SchedulePlanEntity, UUID> {
    Optional<SchedulePlanEntity> findByAccountId(UUID accountId);
    void deleteByAccountId(UUID accountId);
}
