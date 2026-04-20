package de.goaldone.backend.repository;

import de.goaldone.backend.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {
    List<AppointmentEntity> findByAccountId(UUID accountId);

    Optional<AppointmentEntity> findByIdAndAccountId(UUID id, UUID accountId);

    List<AppointmentEntity> findByAccountIdAndDateBetween(UUID accountId, LocalDate from, LocalDate to);
}
