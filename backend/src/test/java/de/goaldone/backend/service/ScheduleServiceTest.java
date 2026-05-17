package de.goaldone.backend.service;

import de.goaldone.backend.entity.ScheduleEntryEntity;
import de.goaldone.backend.entity.SchedulePlanEntity;
import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.SchedulePlanRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScheduleServiceTest {

    @Mock
    private TasksService taskService;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private SchedulePlanRepository schedulePlanRepository;

    @Mock
    private ScheduleEntryRepository scheduleEntryRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private Jwt mockJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
    }

    private GenerateScheduleRequest createGenerateScheduleRequest(LocalDateTime fromDate) {
        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setFrom(OffsetDateTime.of(fromDate, ZoneOffset.UTC));
        return request;
    }

    private UserAccountEntity createUserAccount(UUID accountId, String zitadelSub) {
        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub(zitadelSub);
        account.setWorkingTimes(List.of());
        return account;
    }

    // =========================
    // SINGLE ACCOUNT VALIDATION
    // =========================

    private AppointmentListResponse emptyAppointmentResponse() {
        AppointmentListResponse response = new AppointmentListResponse();
        response.setAppointments(List.of());
        return response;
    }

    @Test
    void invalidFromDate_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().minusDays(1));

        UserAccountEntity account = createUserAccount(accountId, "user-1");

        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("From date cannot be in the past"));
    }

    @Test
    void accountNotFound_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().plusDays(1));

        when(userAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("Account not found"));
    }

    @Test
    void userHasNoAccess_returnsWarning() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().plusDays(1));

        UserAccountEntity account = createUserAccount(accountId, "different-user");

        when(userAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(false);

        ScheduleResponse response = scheduleService.generateSingleAccountSchedule(jwt, accountId, request, 5000);

        assertEquals(1, response.getWarnings().size());

        ScheduleWarning warning = response.getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("does not have access"));
    }

    // =========================
    // MULTI ACCOUNT VALIDATION
    // =========================

    @Test
    void emptyAccountList_returnsWarning() {
        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().plusDays(1));

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(), request, 5000);

        assertEquals(1, responses.size());
        assertEquals(1, responses.getFirst().getWarnings().size());

        ScheduleWarning warning = responses.getFirst().getWarnings().getFirst();
        assertEquals(ScheduleWarning.TypeEnum.OTHER, warning.getType());
        assertTrue(warning.getMessage().contains("No accounts linked to user"));
    }

    @Test
    void multiAccount_withInvalidAccount_returnsWarningForInvalid() {
        UUID validId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();

        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().plusDays(1));

        UserAccountEntity validAccount = createUserAccount(validId, "user-1");

        when(userAccountRepository.findById(validId)).thenReturn(Optional.of(validAccount));
        when(userAccountRepository.findByIdWithWorkingTimes(validId)).thenReturn(Optional.of(validAccount));
        when(userAccountRepository.findById(invalidId)).thenReturn(Optional.empty());
        when(userIdentityService.hasUserAccessToAccount(jwt, validId))
                .thenReturn(true);
        when(taskService.getTasksForAccountId(jwt, validId)).thenReturn(List.of());
        when(appointmentService.listAppointments(validId, jwt)).thenReturn(emptyAppointmentResponse());

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(validId, invalidId), request, 5000);

        assertEquals(2, responses.size());

        long warnings = responses.stream()
                .filter(response -> response.getWarnings() != null)
                .filter(response -> !response.getWarnings().isEmpty())
                .filter(response -> response.getWarnings().getFirst().getMessage().contains("Account not found"))
                .count();

        assertEquals(1, warnings);
    }

    @Test
    void multiAccount_withUnauthorizedAccount_returnsWarning() {
        UUID validId = UUID.randomUUID();
        UUID unauthorizedId = UUID.randomUUID();

        Jwt jwt = mockJwt();
        GenerateScheduleRequest request = createGenerateScheduleRequest(LocalDateTime.now().plusDays(1));

        UserAccountEntity valid = createUserAccount(validId, "user-1");
        UserAccountEntity unauthorized = createUserAccount(unauthorizedId, "other-user");

        when(userAccountRepository.findById(validId)).thenReturn(Optional.of(valid));
        when(userAccountRepository.findByIdWithWorkingTimes(validId)).thenReturn(Optional.of(valid));
        when(userAccountRepository.findById(unauthorizedId)).thenReturn(Optional.of(unauthorized));
        when(userIdentityService.hasUserAccessToAccount(jwt, validId))
                .thenReturn(true);
        when(userIdentityService.hasUserAccessToAccount(jwt, unauthorizedId))
                .thenReturn(false);
        when(taskService.getTasksForAccountId(jwt, validId)).thenReturn(List.of());
        when(appointmentService.listAppointments(validId, jwt)).thenReturn(emptyAppointmentResponse());

        List<ScheduleResponse> responses =
                scheduleService.generateMultiAccountSchedule(jwt, List.of(validId, unauthorizedId), request, 5000);

        assertEquals(2, responses.size());

        long warnings = responses.stream()
                .filter(response -> response.getWarnings() != null)
                .filter(response -> !response.getWarnings().isEmpty())
                .filter(response -> response.getWarnings().getFirst().getMessage().contains("does not have access"))
                .count();

        assertEquals(1, warnings);
    }

    // ================================
    // CREATE SCHEDULING CONTEXT TESTS
    // (Tests getAvailableTimeSlots indirectly)
    // ================================

    @Test
    void createSchedulingContext_noWorkingTimes_returnsContextWithDefaultWorkingTimes(){
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.now().plusDays(1);

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of());

        when(userAccountRepository.findByIdWithWorkingTimes(accountId)).thenReturn(Optional.of(account));
        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(emptyAppointmentResponse());

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        assertNotNull(context);
        assertEquals(fromDate, context.fromDate());

        WorkingTimeEntity defaultWorkingTimes = new WorkingTimeEntity();
        defaultWorkingTimes.setDays(Set.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        ));
        defaultWorkingTimes.setStartTime(LocalTime.of(8, 0));
        defaultWorkingTimes.setEndTime(LocalTime.of(17, 0));

        assertEquals(List.of(defaultWorkingTimes), context.workingTimes());
    }

    @Test
    void createSchedulingContext_noAppointments_returnsFullWorkingHours() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.of(LocalDate.of(2026, 5, 12),  LocalTime.of(7, 0));

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(17, 0));
        workingTime.setDays(new HashSet<>(List.of(DayOfWeek.TUESDAY)));
        workingTime.setCreatedAt(Instant.now());

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime));

        when(userAccountRepository.findByIdWithWorkingTimes(accountId)).thenReturn(Optional.of(account));
        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(emptyAppointmentResponse());

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        assertNotNull(context);
        assertFalse(context.availableSlots().isEmpty(), "Should have at least one slot");

        TimeSlot slot = context.availableSlots().getFirst();
        assertEquals(fromDate.toLocalDate(), slot.date());
        assertEquals(LocalTime.of(9, 0), slot.startTime());
        assertEquals(LocalTime.of(17, 0), slot.endTime());
    }

    @Test
    void createSchedulingContext_withAppointments_returnsGapsBetweenAppointments() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(17, 0));
        workingTime.setDays(new HashSet<>(List.of(DayOfWeek.MONDAY)));
        workingTime.setCreatedAt(Instant.now());

        // Create two appointments: 10:00-11:00 and 14:00-15:00
        Appointment apt1 = new Appointment();
        apt1.setDate(fromDate.toLocalDate());
        apt1.setStartTime("10:00");
        apt1.setEndTime("11:00");
        apt1.setAppointmentType(AppointmentType.ONE_TIME);

        Appointment apt2 = new Appointment();
        apt2.setDate(fromDate.toLocalDate());
        apt2.setStartTime("14:00");
        apt2.setEndTime("15:00");
        apt2.setAppointmentType(AppointmentType.ONE_TIME);

        AppointmentListResponse appointmentResponse = new AppointmentListResponse();
        appointmentResponse.setAppointments(List.of(apt1, apt2));

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime));

        when(userAccountRepository.findByIdWithWorkingTimes(accountId)).thenReturn(Optional.of(account));
        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(appointmentResponse);

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        List<TimeSlot> slotsForFromDate = context.availableSlots()
                .stream()
                .filter(slot -> fromDate.toLocalDate().equals(slot.date()))
                .toList();

        assertNotNull(context);

        // Should have 3 slots: 09:00-10:00, 11:00-14:00, 15:00-17:00
        TimeSlot slot1 = new TimeSlot(fromDate.toLocalDate(), LocalTime.of(9,  0), LocalTime.of(10, 0));
        TimeSlot slot2 = new TimeSlot(fromDate.toLocalDate(), LocalTime.of(11, 0), LocalTime.of(14, 0));
        TimeSlot slot3 = new TimeSlot(fromDate.toLocalDate(), LocalTime.of(15, 0), LocalTime.of(17, 0));

        assertEquals(slot1, slotsForFromDate.getFirst());
        assertEquals(slot2, slotsForFromDate.get(1));
        assertEquals(slot3, slotsForFromDate.get(2));
    }

    @Test
    void createSchedulingContext_withRecurringBreak_returnsGapsBetweenAppointments() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(17, 0));
        workingTime.setDays(new HashSet<>(List.of(DayOfWeek.MONDAY)));
        workingTime.setCreatedAt(Instant.now());

        // Create recurring break (Mon-Fri, 12-13)
        Appointment breakApt = new Appointment();
        breakApt.setStartTime("12:00");
        breakApt.setEndTime("13:00");
        breakApt.setAppointmentType(AppointmentType.RECURRING);
        breakApt.rrule("FREQ=WEEKLY;COUNT=30;WKST=MO;BYDAY=MO,TU,WE,FR");

        AppointmentListResponse appointmentResponse = new AppointmentListResponse();
        appointmentResponse.setAppointments(List.of(breakApt));

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime));

        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(appointmentResponse);

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        List<TimeSlot> slotsForFromDate = context.availableSlots().stream()
                .filter(timeSlot -> timeSlot.date() == fromDate.toLocalDate())
                .toList();
        TimeSlot slot1 = new TimeSlot(fromDate.toLocalDate(), LocalTime.of(8, 0), LocalTime.of(12, 0));
        TimeSlot slot2 = new TimeSlot(fromDate.toLocalDate(), LocalTime.of(13, 0), LocalTime.of(17, 0));


        assertNotNull(context);
        assertEquals(2, slotsForFromDate.size());
        assertEquals(slot1, slotsForFromDate.getFirst());
        assertEquals(slot2, slotsForFromDate.getLast());
    }

    @Test
    void createSchedulingContext_multipleWeekdays_returnsMultipleSlotsPerWeek() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(17, 0));
        workingTime.setDays(new HashSet<>(List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY
        )));
        workingTime.setCreatedAt(Instant.now());

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime));

        when(userAccountRepository.findByIdWithWorkingTimes(accountId)).thenReturn(Optional.of(account));
        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(emptyAppointmentResponse());

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        assertNotNull(context);
        // Should have at least 12 slots (3 per day * 4 weeks)
        assertEquals(12, context.availableSlots().size(), "Expected exactly 12 slots, got " + context.availableSlots().size());
    }

    @Test
    void createSchedulingContext_multipleWorkingTimeDefinitions_returnsCombinedSlots() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        LocalDateTime fromDate = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        // First working time: Monday 09:00-17:00
        WorkingTimeEntity workingTime1 = new WorkingTimeEntity();
        workingTime1.setId(UUID.randomUUID());
        workingTime1.setStartTime(LocalTime.of(9, 0));
        workingTime1.setEndTime(LocalTime.of(17, 0));
        workingTime1.setDays(new HashSet<>(List.of(DayOfWeek.MONDAY)));
        workingTime1.setCreatedAt(Instant.now());

        // Second working time: Tuesday 08:00-16:00
        WorkingTimeEntity workingTime2 = new WorkingTimeEntity();
        workingTime2.setId(UUID.randomUUID());
        workingTime2.setStartTime(LocalTime.of(8, 0));
        workingTime2.setEndTime(LocalTime.of(16, 0));
        workingTime2.setDays(new HashSet<>(List.of(DayOfWeek.TUESDAY)));
        workingTime2.setCreatedAt(Instant.now());

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime1, workingTime2));

        when(userAccountRepository.findByIdWithWorkingTimes(accountId)).thenReturn(Optional.of(account));
        when(taskService.getTasksForAccountId(jwt, accountId)).thenReturn(List.of());
        when(appointmentService.listAppointments(accountId, jwt)).thenReturn(emptyAppointmentResponse());

        SchedulingContext context = scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        assertNotNull(context);
        // Should collect slots from both working time definitions (Monday and Tuesday slots)
        assertEquals(8, context.availableSlots().size(), "Expected exactly 8 slots (4 weeks * 2 days), got " + context.availableSlots().size());
    }

    @Test
    void createSchedulingContext_fromDateTimeInWorkingDay_startsPlanningFromCurrentTime() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        // Monday 14:00
        LocalDateTime fromDate = LocalDateTime.of(
                LocalDate.of(2026, 5, 11),
                LocalTime.of(14, 0)
        );

        WorkingTimeEntity workingTime = new WorkingTimeEntity();
        workingTime.setId(UUID.randomUUID());
        workingTime.setStartTime(LocalTime.of(9, 0));
        workingTime.setEndTime(LocalTime.of(17, 0));
        workingTime.setDays(Set.of(DayOfWeek.MONDAY));
        workingTime.setCreatedAt(Instant.now());

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub("user-1");
        account.setWorkingTimes(List.of(workingTime));

        when(userAccountRepository.findByIdWithWorkingTimes(accountId))
                .thenReturn(Optional.of(account));

        when(taskService.getTasksForAccountId(jwt, accountId))
                .thenReturn(List.of());

        when(appointmentService.listAppointments(accountId, jwt))
                .thenReturn(emptyAppointmentResponse());

        SchedulingContext context =
                scheduleService.createSchedulingContext(jwt, accountId, fromDate);

        assertNotNull(context);
        assertFalse(context.availableSlots().isEmpty());

        List<TimeSlot> mondaySlots = context.availableSlots().stream()
                .filter(slot -> slot.date().equals(fromDate.toLocalDate()))
                .toList();

        assertEquals(1, mondaySlots.size());

        TimeSlot expectedSlot = new TimeSlot(
                fromDate.toLocalDate(),
                LocalTime.of(14, 0),
                LocalTime.of(17, 0)
        );

        assertEquals(expectedSlot, mondaySlots.getFirst());
    }

    // =========================
    // MARK ENTRY DONE TESTS
    // =========================

    /**
     * Builds a minimal ScheduleEntryEntity suitable for markEntryDone unit tests.
     * All nullable fields are omitted; only non-null fields required by the mapper are set.
     */
    private ScheduleEntryEntity buildEntry(UUID id, UUID accountId, UUID planId,
                                           UUID originalItemId, Integer chunkIndex, Integer totalChunks) {
        SchedulePlanEntity plan = new SchedulePlanEntity();
        plan.setId(planId);

        ScheduleEntryEntity entry = new ScheduleEntryEntity();
        entry.setId(id);
        entry.setPlan(plan);
        entry.setAccountId(accountId);
        entry.setOriginalItemId(originalItemId);
        entry.setChunkIndex(chunkIndex);
        entry.setTotalChunks(totalChunks);
        entry.setIsCompleted(false);
        entry.setIsBreak(false);
        entry.setEntryType("TASK");
        entry.setStartAt(LocalDateTime.of(2026, 5, 19, 9, 0));
        entry.setEndAt(LocalDateTime.of(2026, 5, 19, 10, 0));
        entry.setOccurrenceDate(LocalDate.of(2026, 5, 19));
        entry.setOriginalItemTitle("Test Task");
        return entry;
    }

    @Test
    void markEntryDone_noAccess_throwsForbidden() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () ->
                scheduleService.markEntryDone(jwt, accountId, UUID.randomUUID(), MarkScheduleEntryScope.CHUNK));
    }

    @Test
    void markEntryDone_entryNotFound_throwsNotFound() {
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(scheduleEntryRepository.findByIdAndAccountId(entryId, accountId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () ->
                scheduleService.markEntryDone(jwt, accountId, entryId, MarkScheduleEntryScope.CHUNK));
    }

    @Test
    void markEntryDone_taskScope_completesAllEntriesAndTask() {
        UUID accountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID originalItemId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        ScheduleEntryEntity entry = buildEntry(entryId, accountId, planId, originalItemId, 0, 2);
        ScheduleEntryEntity sibling = buildEntry(UUID.randomUUID(), accountId, planId, originalItemId, 1, 2);

        TaskEntity task = new TaskEntity();
        task.setId(originalItemId);
        task.setStatus(TaskStatus.OPEN);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(scheduleEntryRepository.findByIdAndAccountId(entryId, accountId)).thenReturn(Optional.of(entry));
        when(scheduleEntryRepository.findByPlanIdAndOriginalItemId(planId, originalItemId))
                .thenReturn(List.of(entry, sibling));
        when(scheduleEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.findByIdAndAccountId(originalItemId, accountId)).thenReturn(Optional.of(task));

        MarkScheduleEntryResponse response = scheduleService.markEntryDone(jwt, accountId, entryId, MarkScheduleEntryScope.TASK);

        assertTrue(entry.getIsCompleted());
        assertTrue(sibling.getIsCompleted());
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(2, response.getUpdatedEntries().size());
    }

    @Test
    void markEntryDone_chunkScope_singleChunk_upgradesEntireTask() {
        UUID accountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID originalItemId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        ScheduleEntryEntity entry = buildEntry(entryId, accountId, planId, originalItemId, 0, 1);

        TaskEntity task = new TaskEntity();
        task.setId(originalItemId);
        task.setStatus(TaskStatus.OPEN);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(scheduleEntryRepository.findByIdAndAccountId(entryId, accountId)).thenReturn(Optional.of(entry));
        when(scheduleEntryRepository.save(entry)).thenReturn(entry);
        when(scheduleEntryRepository.findByPlanIdAndOriginalItemId(planId, originalItemId))
                .thenReturn(List.of(entry));
        when(scheduleEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.findByIdAndAccountId(originalItemId, accountId)).thenReturn(Optional.of(task));

        MarkScheduleEntryResponse response = scheduleService.markEntryDone(jwt, accountId, entryId, MarkScheduleEntryScope.CHUNK);

        assertTrue(entry.getIsCompleted());
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(1, response.getUpdatedEntries().size());
    }

    @Test
    void markEntryDone_chunkScope_lastRemainingChunk_upgradesEntireTask() {
        UUID accountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID originalItemId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        ScheduleEntryEntity entry = buildEntry(entryId, accountId, planId, originalItemId, 2, 3);

        ScheduleEntryEntity sibling1 = buildEntry(UUID.randomUUID(), accountId, planId, originalItemId, 0, 3);
        sibling1.setIsCompleted(true);
        ScheduleEntryEntity sibling2 = buildEntry(UUID.randomUUID(), accountId, planId, originalItemId, 1, 3);
        sibling2.setIsCompleted(true);

        TaskEntity task = new TaskEntity();
        task.setId(originalItemId);
        task.setStatus(TaskStatus.IN_PROGRESS);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(scheduleEntryRepository.findByIdAndAccountId(entryId, accountId)).thenReturn(Optional.of(entry));
        when(scheduleEntryRepository.save(entry)).thenReturn(entry);
        // After marking this entry completed, 0 incomplete entries remain
        when(scheduleEntryRepository.countByPlanIdAndOriginalItemIdAndIsCompletedFalse(planId, originalItemId))
                .thenReturn(0L);
        when(scheduleEntryRepository.findByPlanIdAndOriginalItemId(planId, originalItemId))
                .thenReturn(List.of(sibling1, sibling2, entry));
        when(scheduleEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.findByIdAndAccountId(originalItemId, accountId)).thenReturn(Optional.of(task));

        MarkScheduleEntryResponse response = scheduleService.markEntryDone(jwt, accountId, entryId, MarkScheduleEntryScope.CHUNK);

        assertTrue(entry.getIsCompleted());
        assertTrue(sibling1.getIsCompleted());
        assertTrue(sibling2.getIsCompleted());
        assertEquals(TaskStatus.DONE, task.getStatus());
        assertEquals(3, response.getUpdatedEntries().size());
    }

    @Test
    void markEntryDone_chunkScope_middleChunk_marksOnlyEntryWithoutCompletingTask() {
        UUID accountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID originalItemId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        ScheduleEntryEntity entry = buildEntry(entryId, accountId, planId, originalItemId, 1, 3);

        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(scheduleEntryRepository.findByIdAndAccountId(entryId, accountId)).thenReturn(Optional.of(entry));
        when(scheduleEntryRepository.save(entry)).thenReturn(entry);
        // 2 remaining incomplete entries — not the last chunk
        when(scheduleEntryRepository.countByPlanIdAndOriginalItemIdAndIsCompletedFalse(planId, originalItemId))
                .thenReturn(2L);

        MarkScheduleEntryResponse response = scheduleService.markEntryDone(jwt, accountId, entryId, MarkScheduleEntryScope.CHUNK);

        assertTrue(entry.getIsCompleted());
        assertEquals(1, response.getUpdatedEntries().size());
        verify(taskRepository, never()).findByIdAndAccountId(any(), any());
    }
}
