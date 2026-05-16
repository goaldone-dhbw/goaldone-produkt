package de.goaldone.backend.service;

import de.goaldone.backend.entity.AppointmentEntity;
import de.goaldone.backend.exception.AppointmentOverlapException;
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

    @InjectMocks
    private AppointmentService appointmentService;

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
        AppointmentCreate req = buildBreakRequest("Mittag", "12:00", "13:00");

        when(appointmentRepository.save(any(AppointmentEntity.class)))
                 .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID()); // simulate @GeneratedValue
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req);

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

        AppointmentEntity e1 = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        AppointmentEntity e2 = buildEntity(accountId, "Kaffeepause", "15:00", "15:15", true);

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(e1, e2));

        AppointmentListResponse response = appointmentService.listAppointments(accountId);

        assertEquals(2, response.getAppointments().size());
    }

    // -------------------------------------------------------------------------
    // Positive: updateAppointment (full replace)
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_validRequest_replacesAllFields() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        AppointmentEntity existing = buildEntity(accountId, "Alt", "10:00", "11:00", true);
        existing.setId(appointmentId);
        Instant originalCreatedAt = existing.getCreatedAt();

        AppointmentCreate update = buildBreakRequest("Neu", "12:00", "13:00");

        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.updateAppointment(accountId, appointmentId, update);

        assertEquals("Neu", result.getTitle());
        assertEquals("12:00", result.getStartTime());
        assertEquals("13:00", result.getEndTime());
        // id and createdAt must remain unchanged
        assertEquals(appointmentId, result.getId());
        assertNotNull(result.getCreatedAt());
        assertEquals(originalCreatedAt.getEpochSecond(), result.getCreatedAt().toInstant().getEpochSecond());
    }

    // -------------------------------------------------------------------------
    // Positive: deleteAppointment
    // -------------------------------------------------------------------------

    @Test
    void deleteAppointment_existingAppointment_deletesFromRepository() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        AppointmentEntity entity = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        entity.setId(appointmentId);

        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(entity));

        appointmentService.deleteAppointment(accountId, appointmentId);

        verify(appointmentRepository).delete(entity);
    }

    // -------------------------------------------------------------------------
    // Negative: endTime vor startTime → 400
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_endTimeBeforeStartTime_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        AppointmentCreate req = buildBreakRequest("Mittag", "14:00", "13:30");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("endTime must be after startTime"));
    }

    // -------------------------------------------------------------------------
    // Negative: fehlender Titel → 400
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_missingTitle_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        AppointmentCreate req = buildBreakRequest(null, "12:00", "13:00");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("title must not be blank"));
    }

    @Test
    void createAppointment_blankTitle_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        AppointmentCreate req = buildBreakRequest("   ", "12:00", "13:00");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Negative: Appointment nicht gefunden → 404
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_notFound_throwsNotFound() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        AppointmentCreate req = buildBreakRequest("Mittag", "12:00", "13:00");

        when(appointmentRepository.findByIdAndAccountId(eq(appointmentId), eq(accountId)))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.updateAppointment(accountId, appointmentId, req));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Negative: RECURRING ohne rrule → 400
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_recurringWithoutRrule_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();

        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Täglich");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule(null); // fehlt absichtlich
        req.setStartTime("12:00");
        req.setEndTime("13:00");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("rrule"));
    }

    // -------------------------------------------------------------------------
    // Negative: Overlapping RECURRING ↔ RECURRING → 409
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_overlappingRecurringBreaks_throwsOverlapException() {
        UUID accountId = UUID.randomUUID();

        // Existing recurring break: Mon-Fri 12:00-13:00
        AppointmentEntity existing = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        // New recurring break: Mon-Wed 12:30-13:30 (overlaps on MO,TU,WE + time overlap)
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Zweite Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE");
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));

        AppointmentOverlapException ex = assertThrows(AppointmentOverlapException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertTrue(ex.getMessage().contains("Mittag"));
    }

    // -------------------------------------------------------------------------
    // Negative: Overlapping ONE_TIME ↔ ONE_TIME → 409
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_overlappingOneTimeAppointments_throwsOverlapException() {
        UUID accountId = UUID.randomUUID();

        // Existing one-time appointment on 2026-05-18 10:00-11:00
        AppointmentEntity existing = buildEntity(accountId, "Arzttermin", "10:00", "11:00", false);
        existing.setAppointmentType(AppointmentType.ONE_TIME);
        existing.setDate(LocalDate.of(2026, 5, 18));
        existing.setRrule(null);

        // New one-time break on same date 10:30-11:30
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.ONE_TIME);
        req.setDate(LocalDate.of(2026, 5, 18));
        req.setStartTime("10:30");
        req.setEndTime("11:30");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));

        AppointmentOverlapException ex = assertThrows(AppointmentOverlapException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertTrue(ex.getMessage().contains("Arzttermin"));
    }

    // -------------------------------------------------------------------------
    // Negative: Overlapping ONE_TIME ↔ RECURRING → 409
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_oneTimeOverlapsRecurring_throwsOverlapException() {
        UUID accountId = UUID.randomUUID();

        // Existing recurring break on MO 12:00-13:00
        AppointmentEntity existing = buildEntity(accountId, "Mittagspause", "12:00", "13:00", true);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO");

        // New one-time appointment on Monday 2026-05-18 12:30-13:30
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Montags-Termin");
        req.setIsBreak(false);
        req.setAppointmentType(AppointmentType.ONE_TIME);
        req.setDate(LocalDate.of(2026, 5, 18)); // Monday
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));

        AppointmentOverlapException ex = assertThrows(AppointmentOverlapException.class,
                () -> appointmentService.createAppointment(accountId, req));

        assertTrue(ex.getMessage().contains("Mittagspause"));
    }

    @Test
    void createAppointment_recurringOverlapsOneTime_throwsOverlapException() {
        UUID accountId = UUID.randomUUID();

        // Existing one-time appointment on Monday 2026-05-18 12:00-13:00
        AppointmentEntity existing = buildEntity(accountId, "Montags-Termin", "12:00", "13:00", false);
        existing.setAppointmentType(AppointmentType.ONE_TIME);
        existing.setDate(LocalDate.of(2026, 5, 18)); // Monday
        existing.setRrule(null);

        // New recurring break on MO 12:30-13:30
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Mittagspause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=MO");
        req.setStartTime("12:30");
        req.setEndTime("13:30");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));

        assertThrows(AppointmentOverlapException.class,
                () -> appointmentService.createAppointment(accountId, req));
    }

    // -------------------------------------------------------------------------
    // Positive: No overlap (different times)
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_noTimeOverlap_succeeds() {
        UUID accountId = UUID.randomUUID();

        // Existing break 12:00-13:00
        AppointmentEntity existing = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        // New break 15:00-15:30 (no time overlap)
        AppointmentCreate req = buildBreakRequest("Kaffeepause", "15:00", "15:30");
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req);

        assertEquals("Kaffeepause", result.getTitle());
    }

    // -------------------------------------------------------------------------
    // Positive: No overlap (different days)
    // -------------------------------------------------------------------------

    @Test
    void createAppointment_noDayOverlap_succeeds() {
        UUID accountId = UUID.randomUUID();

        // Existing break on MO 12:00-13:00
        AppointmentEntity existing = buildEntity(accountId, "Montag Pause", "12:00", "13:00", true);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO");

        // New break on FR 12:00-13:00 (same time but different day)
        AppointmentCreate req = new AppointmentCreate();
        req.setTitle("Freitag Pause");
        req.setIsBreak(true);
        req.setAppointmentType(AppointmentType.RECURRING);
        req.setRrule("FREQ=WEEKLY;BYDAY=FR");
        req.setStartTime("12:00");
        req.setEndTime("13:00");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> {
                    AppointmentEntity e = invocation.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        Appointment result = appointmentService.createAppointment(accountId, req);

        assertEquals("Freitag Pause", result.getTitle());
    }

    // -------------------------------------------------------------------------
    // Positive: Update excludes own ID from overlap check
    // -------------------------------------------------------------------------

    @Test
    void updateAppointment_sameAppointment_doesNotThrowOverlap() {
        UUID accountId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        AppointmentEntity existing = buildEntity(accountId, "Mittag", "12:00", "13:00", true);
        existing.setId(appointmentId);
        existing.setAppointmentType(AppointmentType.RECURRING);
        existing.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        // Update with same times (should not conflict with itself)
        AppointmentCreate req = buildBreakRequest("Mittag Neu", "12:00", "13:00");
        req.setRrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");

        when(appointmentRepository.findByAccountId(accountId)).thenReturn(List.of(existing));
        when(appointmentRepository.findByIdAndAccountId(appointmentId, accountId))
                .thenReturn(Optional.of(existing));
        when(appointmentRepository.save(any(AppointmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.updateAppointment(accountId, appointmentId, req);

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
