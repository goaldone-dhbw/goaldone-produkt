package de.goaldone.backend.service;

import de.goaldone.backend.entity.AppointmentEntity;
import de.goaldone.backend.model.Appointment;
import de.goaldone.backend.model.AppointmentCreate;
import de.goaldone.backend.model.AppointmentListResponse;
import de.goaldone.backend.model.AppointmentType;
import de.goaldone.backend.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @InjectMocks
    private AppointmentService appointmentService;

    private Jwt mockJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
    }

    private AppointmentCreate buildBreakRequest(String title, String startTime, String endTime) {
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle(title);
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=DAILY");
        req.setStartTime(startTime);
        req.setEndTime(endTime);
        return req;
    }

    // -------------------------------------------------------------------------
    // Positive: createAppointment
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_validBreak_returnsCreatedAppointment() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest("Mittag", "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req, jwt);

        assertEquals("Mittag", result.getTitle());
        assertTrue(result.getIsBreak());
        assertEquals("12:00", result.getStartTime());
        assertEquals("13:00", result.getEndTime());
        assertEquals(accountId, result.getAccountId());
        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());

        ArgumentCaptor<AppointmentEntity> captor = ArgumentCaptor.forClass(AppointmentEntity.class);
        verify(appointmentRepository).save(captor.capture());
        assertEquals(accountId, captor.getValue().getAccountId());
        assertTrue(captor.getValue().getIsBreak());
    }

    // -------------------------------------------------------------------------
    // Positive: listAppointments
    // -------------------------------------------------------------------------

    @Test
    void listAppointments_twoExistingBreaks_returnsBothInResponse() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentEntity e1 = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        AppointmentEntity e2 = buildEntity(accountId, "Kaffeepause", "15:00", "15:15", true);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(e1, e2));

        AppointmentListResponse response = appointmentService.listAppointments(accountId, jwt);

        assertEquals(2, response.getAppointments().size());
    }

    // -------------------------------------------------------------------------
    // Positive: updateAppointment
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_validRequest_replacesAllFields() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentEntity existing = buildEntity(accountId, "Alt", "10:00", "11:00", true);
        existing.setId(appointmentId);
        Instant originalCreatedAt = existing.getCreatedAt();

        AppointmentCreate update = buildBreakRequest("Neu", "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.updateAppointment(accountId, appointmentId, update, jwt);

        assertEquals("Neu", result.getTitle());
        assertEquals("12:00", result.getStartTime());
        assertEquals("13:00", result.getEndTime());
        assertEquals(appointmentId, result.getId());
        assertNotNull(result.getCreatedAt());
        assertEquals(
                originalCreatedAt.getEpochSecond(),
                result.getCreatedAt().toInstant().getEpochSecond()
        );
    }

    // -------------------------------------------------------------------------
    // Positive: deleteAppointment
    // -------------------------------------------------------------------------

    @Test
    void deleteAppointment_existingAppointment_deletesFromRepository() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentEntity entity = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        entity.setId(appointmentId);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(entity));

        appointmentService.deleteAppointment(accountId, appointmentId, jwt);

        verify(appointmentRepository).delete(entity);
    }

    // -------------------------------------------------------------------------
    // Negative: invalid time range
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_endTimeBeforeStartTime_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest("Mittag", "14:00", "13:30");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req, jwt));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("endTime must be after startTime"));
    }

    // -------------------------------------------------------------------------
    // Negative: missing title
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_missingTitle_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest(null, "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req, jwt));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("title must not be blank"));
    }

    @Test
    void createAppointment_blankTitle_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest("   ", "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req, jwt));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Negative: missing account access
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_noAccountAccess_throwsForbidden() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest("Mittag", "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req, jwt));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Negative: appointment not found
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_notFound_throwsNotFound() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        AppointmentCreate req = buildBreakRequest("Mittag", "12:00", "13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.findByIdAndAccountId(eq(appointmentId), eq(accountId)))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.updateAppointment(accountId, appointmentId, req, jwt));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Negative: recurring appointment without rrule
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_recurringWithoutRrule_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Täglich");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule(null);
        req.setStartTime("12:00");
        req.setEndTime("13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req, jwt));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("rrule"));
    }

    // -------------------------------------------------------------------------
    // Positive: overlapping RECURRING ↔ RECURRING appointments are allowed
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_overlappingRecurringBreaks_allowsOverlap() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Zweite Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE");
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = assertDoesNotThrow(() ->
                appointmentService.createAppointment(accountId, req, jwt)
        );

        assertNotNull(result);
        assertEquals("Zweite Pause", result.getTitle());
        verify(appointmentRepository).save(any(AppointmentEntity.class));
    }

    // -------------------------------------------------------------------------
    // Positive: overlapping ONE_TIME ↔ ONE_TIME appointments are allowed
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_overlappingOneTimeAppointments_allowsOverlap() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.ONE_TIME);
        req.setDate(LocalDate.of(2026, 5, 18));
        req.setStartTime("10:30");
        req.setEndTime("11:30");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = assertDoesNotThrow(() ->
                appointmentService.createAppointment(accountId, req, jwt)
        );

        assertNotNull(result);
        assertEquals("Pause", result.getTitle());
        verify(appointmentRepository).save(any(AppointmentEntity.class));
    }

    // -------------------------------------------------------------------------
    // Positive: overlapping ONE_TIME ↔ RECURRING appointments are allowed
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_oneTimeOverlapsRecurring_allowsOverlap() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Montags-Termin");
        req.setIsBreak(false);
        req.setAppointmentType(AppointmentType.ONE_TIME);
        req.setDate(LocalDate.of(2026, 5, 18));
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = assertDoesNotThrow(() ->
                appointmentService.createAppointment(accountId, req, jwt)
        );

        assertNotNull(result);
        assertEquals("Montags-Termin", result.getTitle());
        verify(appointmentRepository).save(any(AppointmentEntity.class));
    }

    // -------------------------------------------------------------------------
    // Positive: overlapping RECURRING ↔ ONE_TIME appointments are allowed
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_recurringOverlapsOneTime_allowsOverlap() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Mittagspause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=MO");
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = assertDoesNotThrow(() ->
                appointmentService.createAppointment(accountId, req, jwt)
        );

        assertNotNull(result);
        assertEquals("Mittagspause", result.getTitle());
        verify(appointmentRepository).save(any(AppointmentEntity.class));
    }

    // -------------------------------------------------------------------------
    // Positive: creating appointment with different times succeeds
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_noTimeOverlap_succeeds() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = buildBreakRequest("Kaffeepause", "15:00", "15:30");
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req, jwt);

        assertEquals("Kaffeepause", result.getTitle());
    }

    // -------------------------------------------------------------------------
    // Positive: creating appointment with different days succeeds
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_noDayOverlap_succeeds() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Freitag Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=FR");
        req.setStartTime("12:00");
        req.setEndTime("13:00");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req, jwt);

        assertEquals("Freitag Pause", result.getTitle());
    }

    // -------------------------------------------------------------------------
    // Positive: updateAppointment replaces appointment even with same time range
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_sameAppointment_doesNotThrowOverlap() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        AppointmentEntity existing = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        existing.setId(appointmentId);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        AppointmentCreate req = buildBreakRequest("Mittag Neu", "12:00", "13:00");
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = assertDoesNotThrow(() ->
                appointmentService.updateAppointment(accountId, appointmentId, req, jwt)
        );

        assertEquals("Mittag Neu", result.getTitle());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AppointmentEntity buildEntity(UUID accountId, String title, String startTime, String endTime,
                                          boolean isBreak) {
        AppointmentEntity e = new AppointmentEntity();
        e.setId(UUID.randomUUID());
        e.setAccountId(accountId);
        e.setTitle(title);
        e.setIsBreak(isBreak);
        e.setAppointmentType(AppointmentType.RECURRING);
        e.setRrule("FREQ=DAILY");
        e.setStartTime(LocalTime.parse(startTime));
        e.setEndTime(LocalTime.parse(endTime));
        e.setCreatedAt(Instant.now());
        return e;
    }
}

