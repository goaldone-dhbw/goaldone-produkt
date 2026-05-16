package de.goaldone.backend.service;

import de.goaldone.backend.entity.AppointmentEntity;
import de.goaldone.backend.exception.AppointmentOverlapException;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import de.goaldone.backend.model.AppointmentType;
import de.goaldone.backend.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing appointments and breaks.
 * Handles CRUD operations, validation of time ranges, and overlap detection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;

    /**
     * Creates a new appointment or break for the given account.
     *
     * @param accountId The UUID of the account.
     * @param request   The {@link AppointmentCreate} payload.
     * @return The created {@link Appointment}.
     */
    @Transactional
    public Appointment createAppointment(UUID accountId, AppointmentCreate request) {
        validateRequest(request);
        checkForOverlaps(accountId, request, null);

        AppointmentEntity entity = new AppointmentEntity();
        entity.setAccountId(accountId);
        entity.setCreatedAt(Instant.now());
        applyFields(entity, request);

        AppointmentEntity saved = appointmentRepository.save(entity);
        log.info("Created appointment {} for account {}", saved.getId(), accountId);
        return toModel(saved);
    }

    /**
     * Lists all appointments for the given account.
     *
     * @param accountId The UUID of the account.
     * @return An {@link AppointmentListResponse} containing all appointments.
     */
    @Transactional(readOnly = true)
    public AppointmentListResponse listAppointments(UUID accountId) {
        var appointments = appointmentRepository.findByAccountId(accountId)
                .stream()
                .map(this::toModel)
                .toList();

        return new AppointmentListResponse().appointments(appointments);
    }

    /**
     * Retrieves a single appointment by its ID, verifying account ownership.
     *
     * @param accountId     The UUID of the account.
     * @param appointmentId The UUID of the appointment.
     * @return The found {@link Appointment}.
     */
    @Transactional(readOnly = true)
    public Appointment getAppointment(UUID accountId, UUID appointmentId) {
        AppointmentEntity entity = findEntity(accountId, appointmentId);
        return toModel(entity);
    }

    /**
     * Fully replaces an existing appointment with the provided data.
     *
     * @param accountId     The UUID of the account.
     * @param appointmentId The UUID of the appointment to update.
     * @param request       The {@link AppointmentCreate} payload (full replace).
     * @return The updated {@link Appointment}.
     */
    @Transactional
    public Appointment updateAppointment(UUID accountId, UUID appointmentId, AppointmentCreate request) {
        validateRequest(request);
        checkForOverlaps(accountId, request, appointmentId);

        AppointmentEntity entity = findEntity(accountId, appointmentId);
        applyFields(entity, request);

        AppointmentEntity saved = appointmentRepository.save(entity);
        log.info("Updated appointment {} for account {}", saved.getId(), accountId);
        return toModel(saved);
    }

    /**
     * Permanently deletes an appointment.
     *
     * @param accountId     The UUID of the account.
     * @param appointmentId The UUID of the appointment to delete.
     */
    @Transactional
    public void deleteAppointment(UUID accountId, UUID appointmentId) {
        AppointmentEntity entity = findEntity(accountId, appointmentId);
        appointmentRepository.delete(entity);
        log.info("Deleted appointment {} for account {}", appointmentId, accountId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AppointmentEntity findEntity(UUID accountId, UUID appointmentId) {
        return appointmentRepository.findByIdAndAccountId(appointmentId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Appointment not found: " + appointmentId));
    }

    private void validateRequest(AppointmentCreate request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        if (request.getStartTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime is required");
        }
        if (request.getEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime is required");
        }

        LocalTime start = LocalTime.parse(request.getStartTime(), TIME_FORMATTER);
        LocalTime end = LocalTime.parse(request.getEndTime(), TIME_FORMATTER);
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }

        if (request.getAppointmentType() == AppointmentType.ONE_TIME && request.getDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required for ONE_TIME appointments");
        }
        if (request.getAppointmentType() == AppointmentType.RECURRING
                && (request.getRrule() == null || request.getRrule().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rrule is required for RECURRING appointments");
        }
    }

    /**
     * Checks whether the new/updated appointment overlaps with any existing appointment
     * of the same account.
     */
    private void checkForOverlaps(UUID accountId, AppointmentCreate request, UUID excludeId) {
        LocalTime newStart = LocalTime.parse(request.getStartTime(), TIME_FORMATTER);
        LocalTime newEnd = LocalTime.parse(request.getEndTime(), TIME_FORMATTER);

        List<AppointmentEntity> existing = appointmentRepository.findByAccountId(accountId);

        for (AppointmentEntity other : existing) {
            if (excludeId != null && excludeId.equals(other.getId())) {
                continue;
            }

            boolean timeOverlap = newStart.isBefore(other.getEndTime())
                    && newEnd.isAfter(other.getStartTime());

            if (!timeOverlap) {
                continue;
            }

            boolean dayOverlap = hasDayOverlap(request, other);

            if (dayOverlap) {
                String otherLabel = other.getIsBreak() ? "Pause" : "Termin";
                throw new AppointmentOverlapException(
                        String.format("Zeitüberschneidung mit %s \"%s\" (%s–%s).",
                                otherLabel,
                                other.getTitle(),
                                other.getStartTime().format(TIME_FORMATTER),
                                other.getEndTime().format(TIME_FORMATTER))
                );
            }
        }
    }

    private boolean hasDayOverlap(AppointmentCreate newAppt, AppointmentEntity existing) {
        AppointmentType newType = newAppt.getAppointmentType();
        AppointmentType existingType = existing.getAppointmentType();

        if (newType == AppointmentType.ONE_TIME && existingType == AppointmentType.ONE_TIME) {
            return newAppt.getDate() != null
                    && existing.getDate() != null
                    && newAppt.getDate().equals(existing.getDate());
        }

        if (newType == AppointmentType.RECURRING && existingType == AppointmentType.RECURRING) {
            Set<DayOfWeek> newDays = parseByDays(newAppt.getRrule());
            Set<DayOfWeek> existingDays = parseByDays(existing.getRrule());
            return !Collections.disjoint(newDays, existingDays);
        }

        if (newType == AppointmentType.ONE_TIME && existingType == AppointmentType.RECURRING) {
            if (newAppt.getDate() == null) return false;
            Set<DayOfWeek> recurringDays = parseByDays(existing.getRrule());
            return recurringDays.contains(newAppt.getDate().getDayOfWeek());
        }

        if (newType == AppointmentType.RECURRING && existingType == AppointmentType.ONE_TIME) {
            if (existing.getDate() == null) return false;
            Set<DayOfWeek> recurringDays = parseByDays(newAppt.getRrule());
            return recurringDays.contains(existing.getDate().getDayOfWeek());
        }

        return false;
    }

    private Set<DayOfWeek> parseByDays(String rrule) {
        if (rrule == null || rrule.isBlank()) return Set.of();

        Map<String, DayOfWeek> codeToDay = Map.of(
                "MO", DayOfWeek.MONDAY,
                "TU", DayOfWeek.TUESDAY,
                "WE", DayOfWeek.WEDNESDAY,
                "TH", DayOfWeek.THURSDAY,
                "FR", DayOfWeek.FRIDAY,
                "SA", DayOfWeek.SATURDAY,
                "SU", DayOfWeek.SUNDAY
        );

        return Arrays.stream(rrule.toUpperCase().split(";"))
                .filter(part -> part.startsWith("BYDAY="))
                .findFirst()
                .map(part -> part.substring("BYDAY=".length()))
                .map(byDay -> Arrays.stream(byDay.split(","))
                        .map(codeToDay::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private void applyFields(AppointmentEntity entity, AppointmentCreate request) {
        entity.setTitle(request.getTitle().trim());
        entity.setIsBreak(Boolean.TRUE.equals(request.getIsBreak()));
        entity.setAppointmentType(request.getAppointmentType());
        entity.setDate(request.getDate());
        entity.setStartTime(LocalTime.parse(request.getStartTime(), TIME_FORMATTER));
        entity.setEndTime(LocalTime.parse(request.getEndTime(), TIME_FORMATTER));
        entity.setRrule(request.getRrule());
    }

    private Appointment toModel(AppointmentEntity entity) {
        return new Appointment()
                .id(entity.getId())
                .accountId(entity.getAccountId())
                .title(entity.getTitle())
                .isBreak(entity.getIsBreak())
                .appointmentType(entity.getAppointmentType())
                .date(entity.getDate())
                .startTime(entity.getStartTime() != null ? entity.getStartTime().format(TIME_FORMATTER) : null)
                .endTime(entity.getEndTime() != null ? entity.getEndTime().format(TIME_FORMATTER) : null)
                .rrule(entity.getRrule())
                .createdAt(entity.getCreatedAt() != null
                        ? OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC)
                        : null);
    }
}
