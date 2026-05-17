package de.goaldone.backend.repository;

import de.goaldone.backend.entity.ScheduleEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntryEntity, UUID> {
    List<ScheduleEntryEntity> findByPlanId(UUID planId);
    Optional<ScheduleEntryEntity> findByIdAndAccountId(UUID id, UUID accountId);
    List<ScheduleEntryEntity> findByPlanIdAndOriginalItemId(UUID planId, UUID originalItemId);
}
