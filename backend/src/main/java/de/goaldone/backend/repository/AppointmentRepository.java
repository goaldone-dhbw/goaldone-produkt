package de.goaldone.backend.repository;

import de.goaldone.backend.entity.AppointmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for {@link AppointmentEntity}.
 * Provides standard CRUD operations and custom query methods for appointments.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {
    /**
     * Finds all appointments associated with a specific account ID.
     *
     * @param accountId The UUID of the account.
     * @return A list of {@link AppointmentEntity} objects.
     */
    List<AppointmentEntity> findByAccountId(UUID accountId);

    /**
     * Finds an appointment by its ID and account ID.
     *
     * @param id        The UUID of the appointment.
     * @param accountId The UUID of the account.
     * @return An {@link Optional} containing the appointment entity if found, or empty otherwise.
     */
    Optional<AppointmentEntity> findByIdAndAccountId(UUID id, UUID accountId);

    /**
     * Finds all appointments for a specific account within a date range.
     *
     * @param accountId The UUID of the account.
     * @param from      The start date (inclusive).
     * @param to        The end date (inclusive).
     * @return A list of {@link AppointmentEntity} objects within the specified range.
     */
    List<AppointmentEntity> findByAccountIdAndDateBetween(UUID accountId, LocalDate from, LocalDate to);
}
