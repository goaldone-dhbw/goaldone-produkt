package de.goaldone.backend.service;

import de.goaldone.backend.entity.AppointmentEntity;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import de.goaldone.backend.model.AppointmentType;
import de.goaldone.backend.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service for managing appointments and breaks.
 * Handles CRUD operations, validation of required fields and time ranges,
 * and account ownership checks.
 *
 * Overlapping appointments and breaks are allowed. They are treated as
 * separate blocking entries for schedule generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final UserIdentityService userIdentityService;

    @Transactional
    public Appointment createAppointment(UUID accountId, AppointmentCreate request, Jwt jwt) {
        checkAccess(jwt, accountId);
        validateRequest(request);

        AppointmentEntity entity = new AppointmentEntity();
        entity.setAccountId(accountId);
        entity.setCreatedAt(Instant.now());
        applyFields(entity, request);

        AppointmentEntity saved = appointmentRepository.save(entity);
        log.info("Created appointment {} for account {}", saved.getId(), accountId);
        return toModel(saved);
    }

    @Transactional(readOnly = true)
    public AppointmentListResponse listAppointments(UUID accountId, Jwt jwt) {
        checkAccess(jwt, accountId);

        var appointments = appointmentRepository.findByAccountId(accountId)
                .stream()
                .map(this::toModel)
                .toList();

        return new AppointmentListResponse().appointments(appointments);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointment(UUID accountId, UUID appointmentId, Jwt jwt) {
        checkAccess(jwt, accountId);
        AppointmentEntity entity = findEntity(accountId, appointmentId);
        return toModel(entity);
    }

    @Transactional
    public Appointment updateAppointment(UUID accountId, UUID appointmentId, AppointmentCreate request, Jwt jwt) {
        checkAccess(jwt, accountId);
        validateRequest(request);

        AppointmentEntity entity = findEntity(accountId, appointmentId);
        applyFields(entity, request);

        AppointmentEntity saved = appointmentRepository.save(entity);
        log.info("Updated appointment {} for account {}", saved.getId(), accountId);
        return toModel(saved);
    }

    @Transactional
    public void deleteAppointment(UUID accountId, UUID appointmentId, Jwt jwt) {
        checkAccess(jwt, accountId);
        AppointmentEntity entity = findEntity(accountId, appointmentId);
        appointmentRepository.delete(entity);
        log.info("Deleted appointment {} for account {}", appointmentId, accountId);
    }

    private void checkAccess(Jwt jwt, UUID accountId) {
        if (!userIdentityService.hasUserAccessToAccount(jwt, accountId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User does not have access to account " + accountId
            );
        }
    }

    private AppointmentEntity findEntity(UUID accountId, UUID appointmentId) {
        return appointmentRepository.findByIdAndAccountId(appointmentId, accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Appointment not found: " + appointmentId
                ));
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "date is required for ONE_TIME appointments"
            );
        }

        if (request.getAppointmentType() == AppointmentType.RECURRING
                && (request.getRrule() == null || request.getRrule().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "rrule is required for RECURRING appointments"
            );
        }
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
                .startTime(entity.getStartTime() != null
                        ? entity.getStartTime().format(TIME_FORMATTER)
                        : null)
                .endTime(entity.getEndTime() != null
                        ? entity.getEndTime().format(TIME_FORMATTER)
                        : null)
                .rrule(entity.getRrule())
                .createdAt(entity.getCreatedAt() != null
                        ? OffsetDateTime.ofInstant(entity.getCreatedAt(), ZoneOffset.UTC)
                        : null);
    }
}