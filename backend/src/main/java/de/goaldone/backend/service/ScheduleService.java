package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.entity.ScheduleEntryEntity;
import de.goaldone.backend.entity.SchedulePlanEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.SchedulePlanRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.ScheduleMapper;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScheduleService {

    Solver solver = new Solver();

    private final TasksService taskService;
    private final AppointmentService appointmentService;
    private final UserAccountRepository userAccountRepository;
    private final @Lazy UserIdentityService userIdentityService;
    private final SchedulePlanRepository schedulePlanRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final ScheduleMapper scheduleMapper;

    @Autowired
    public ScheduleService(TasksService taskService,
                           AppointmentService appointmentService,
                           UserAccountRepository userAccountRepository,
                           @Lazy UserIdentityService userIdentityService,
                           SchedulePlanRepository schedulePlanRepository,
                           ScheduleEntryRepository scheduleEntryRepository,
                           TaskRepository taskRepository) {
        this.taskService = taskService;
        this.appointmentService = appointmentService;
        this.userAccountRepository = userAccountRepository;
        this.userIdentityService = userIdentityService;
        this.schedulePlanRepository = schedulePlanRepository;
        this.scheduleEntryRepository = scheduleEntryRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = new ObjectMapper();
        this.scheduleMapper = new ScheduleMapper(this.objectMapper);
    }

    public ScheduleService(TasksService taskService,
                           AppointmentService appointmentService,
                           UserAccountRepository userAccountRepository,
                           UserIdentityService userIdentityService) {
        this(taskService, appointmentService, userAccountRepository, userIdentityService,
                null, null, null);
    }

    /**
     * Generates schedules for multiple accounts asynchronously.
     *
     * @param jwt                 JWT token containing user information
     * @param accountIds          List of account IDs for which schedules should be generated
     * @param request             Request parameters, for example the start date
     * @param timeoutMilliseconds Maximum time to wait for each account's schedule generation
     * @return A list containing one schedule response per account
     */
    public List<ScheduleResponse> generateMultiAccountSchedule(
            Jwt jwt,
            List<UUID> accountIds,
            GenerateScheduleRequest request,
            long timeoutMilliseconds) {

        // No accounts linked to identity
        if (accountIds.isEmpty()) {
            return List.of(createErrorResponse("No accounts linked to user"));
        }

        // Generate schedules in parallel
        try (ExecutorService executor = Executors.newFixedThreadPool(accountIds.size())) {

            // Create schedule for each account simultaneously
            List<CompletableFuture<ScheduleResponse>> futures = accountIds.stream()
                    .map(accountId ->
                            CompletableFuture.supplyAsync(
                                            () -> generateSchedule(jwt, accountId, request.getFrom(), timeoutMilliseconds), executor
                                    )
                                    // If task takes too long -> complete with null instead of blocking
                                    .completeOnTimeout(createErrorResponse("Schedule generation timed out"), timeoutMilliseconds, TimeUnit.MILLISECONDS) //TODO: adjust timeout

                                    // Handle exceptions per task (prevents whole pipeline from failing)
                                    .exceptionally(ex -> {
                                      log.error("Error generating schedule: {}", ex.getMessage(), ex);
                                      return createErrorResponse("Schedule generation failed: " + ex.getMessage());
                                    })
                    ).toList();

            // Collect results
            return futures.stream()
                    .map(CompletableFuture::join)   // non-blocking due to completeOnTimeout
                    .filter(Objects::nonNull)       // remove timed-out or failed tasks
                    .toList();
        } catch (Exception e) {
            log.error("Failed to initialize account scheduling", e);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Failed to initialize account scheduling", e);
        }
    }

    /**
     * Generates schedule for a single account with exception handling
     *
     * @param jwt                     JWT token containing user information
     * @param accountId               Specific account
     * @param generateScheduleRequest Request parameters
     * @param timeoutMilliseconds     Maximum time to wait for schedule generation before giving up (in milliseconds)
     * @return ScheduleResponse with schedule or error warnings
     */
    @Transactional
    public ScheduleResponse generateSingleAccountSchedule(
            Jwt jwt,
            UUID accountId,
            GenerateScheduleRequest generateScheduleRequest,
            long timeoutMilliseconds) {

        try  {
            return generateSchedule(jwt, accountId, generateScheduleRequest.getFrom(), timeoutMilliseconds);
        } catch (Exception ex) {
            return createErrorResponse("Schedule generation failed: " + ex.getMessage());
        }
    }

    /**
     * Orders the generation of a scheduling for an account
     *
     * @param accountId Specific account for which the schedule should be generated
     * @param fromDate Date from which on the schedule should be generated
     * @return Response containing
     * - the generated schedule,
     * - the scheduleScore,
     * - constraint warnings
     */
    @Transactional
    public ScheduleResponse generateSchedule(Jwt jwt, UUID accountId, OffsetDateTime fromDate, long timeoutMs) {

        validateRequest(jwt, accountId, fromDate);

        LocalDateTime scheduleFromDateTime = getScheduleFromDateTime(LocalDateTime.ofInstant(fromDate.toInstant(), ZoneId.systemDefault()));
        SchedulingContext schedulingContext = createSchedulingContext(jwt, accountId, scheduleFromDateTime);
        SchedulingResult bestResult = solver.createSchedule(schedulingContext, timeoutMs);

        Map<ScheduledChunk, UUID> chunkIdMap = persistScheduleAndGetIds(accountId, schedulingContext, bestResult);

        return this.scheduleMapper.mapToScheduleResultWithIds(
                accountId, schedulingContext, bestResult, chunkIdMap
        );
    }

    private Map<ScheduledChunk, UUID> persistScheduleAndGetIds(UUID accountId, SchedulingContext context,
                                                               SchedulingResult result) {
        if (schedulePlanRepository == null || scheduleEntryRepository == null) {
            return result.schedule().scheduledChunks().stream()
                    .collect(LinkedHashMap::new, (map, chunk) -> map.put(chunk, null), Map::putAll);
        }

        schedulePlanRepository.findByAccountId(accountId)
                .ifPresent(existing -> {
                    schedulePlanRepository.delete(existing);
                    schedulePlanRepository.flush();
                });

        String warningsJson = toJson(result.scheduleWarnings());
        String unscheduledJson = toJson(result.schedule().unscheduledTasks());

        LocalDate fromDate = context.fromDate().toLocalDate();
        LocalDate toDate = result.schedule().scheduledChunks().stream()
                .map(c -> c.slot().date())
                .max(LocalDate::compareTo)
                .orElse(fromDate);
        int totalWorkMinutes = result.schedule().scheduledChunks().stream()
                .mapToInt(c -> c.slot().durationMinutes())
                .sum();

        SchedulePlanEntity plan = new SchedulePlanEntity();
        plan.setAccountId(accountId);
        plan.setGeneratedAt(Instant.now());
        plan.setFromDate(fromDate);
        plan.setToDate(toDate);
        plan.setScore(result.score());
        plan.setTotalWorkMinutes(totalWorkMinutes);
        plan.setWarningsJson(warningsJson);
        plan.setUnscheduledTasksJson(unscheduledJson);

        List<ScheduleEntryEntity> entries = new ArrayList<>();
        for (ScheduledChunk chunk : result.schedule().scheduledChunks()) {
            ScheduleEntryEntity entry = new ScheduleEntryEntity();
            entry.setPlan(plan);
            entry.setAccountId(accountId);
            entry.setStartAt(LocalDateTime.of(chunk.date(), chunk.startTime()));
            entry.setEndAt(LocalDateTime.of(chunk.date(), chunk.endTime()));
            entry.setOccurrenceDate(chunk.date());
            boolean isBreak = chunk.chunk().isBreak();
            entry.setEntryType(isBreak ? "APPOINTMENT" : "TASK");
            entry.setIsBreak(isBreak);
            entry.setIsAutomatedBreak(isBreak);
            entry.setIsCompleted(false);
            entry.setOriginalItemId(chunk.chunk().taskId());
            entry.setOriginalItemTitle(chunk.chunk().taskTitle());
            entry.setChunkIndex(chunk.chunk().chunkIndex());
            entry.setTotalChunks(chunk.chunk().totalChunks());
            entries.add(entry);
        }
        plan.setEntries(entries);

        SchedulePlanEntity saved = schedulePlanRepository.saveAndFlush(plan);

        Map<String, UUID> keyToId = new HashMap<>();
        for (ScheduleEntryEntity savedEntry : saved.getEntries()) {
            String key = savedEntry.getOriginalItemId() + ":" + savedEntry.getChunkIndex();
            keyToId.put(key, savedEntry.getId());
        }

        Map<ScheduledChunk, UUID> chunkIdMap = new LinkedHashMap<>();
        for (ScheduledChunk chunk : result.schedule().scheduledChunks()) {
            String key = chunk.chunk().taskId() + ":" + chunk.chunk().chunkIndex();
            chunkIdMap.put(chunk, keyToId.get(key));
        }
        return chunkIdMap;
    }

    @Transactional(readOnly = true)
    public ScheduleResponse loadSingleAccountSchedule(Jwt jwt, UUID accountId) {
        if (!userIdentityService.hasUserAccessToAccount(jwt, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Der Nutzer hat kein Zugriff auf den Account.");
        }

        SchedulePlanEntity plan = schedulePlanRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Für den Account wurde kein Plan gefunden."));

        List<ScheduleEntryEntity> entries = scheduleEntryRepository.findByPlanId(plan.getId());
        List<Appointment> appointments = Optional.ofNullable(appointmentService.listAppointments(accountId, jwt).getAppointments())
                .orElse(List.of());

        return scheduleMapper.mapPlanToResponse(plan, entries, appointments);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> loadAllAccountsSchedules(Jwt jwt, List<UUID> accountIds) {
        return accountIds.stream()
                .map(accountId -> {
                    try {
                        return loadSingleAccountSchedule(jwt, accountId);
                    } catch (ResponseStatusException e) {
                        if (e.getStatusCode().value() == 404) {
                            return null;
                        }
                        throw e;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public MarkScheduleEntryResponse markEntryDone(Jwt jwt, UUID entryId, MarkScheduleEntryScope scope) {
        ScheduleEntryEntity targetEntry = scheduleEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Schedule entry not found: " + entryId));

        UUID accountId = targetEntry.getAccountId();

        if (!userIdentityService.hasUserAccessToAccount(jwt, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "User does not have access to account " + accountId);
        }

        List<ScheduleEntryEntity> updatedEntities;

        if (scope == MarkScheduleEntryScope.TASK) {
            List<ScheduleEntryEntity> taskEntries = scheduleEntryRepository
                    .findByPlanIdAndOriginalItemId(targetEntry.getPlan().getId(), targetEntry.getOriginalItemId());
            taskEntries.forEach(entry -> entry.setIsCompleted(true));
            updatedEntities = scheduleEntryRepository.saveAll(taskEntries);

            if (targetEntry.getOriginalItemId() != null) {
                taskRepository.findByIdAndAccountId(targetEntry.getOriginalItemId(), accountId)
                        .ifPresent(task -> {
                            task.setStatus(TaskStatus.DONE);
                            taskRepository.save(task);
                        });
            }
        } else {
            targetEntry.setIsCompleted(true);
            scheduleEntryRepository.save(targetEntry);

            boolean isSingleChunk = targetEntry.getTotalChunks() == null || targetEntry.getTotalChunks() <= 1;
            boolean isLastChunk = !isSingleChunk
                    && targetEntry.getOriginalItemId() != null
                    && scheduleEntryRepository.countByPlanIdAndOriginalItemIdAndIsCompletedFalse(
                            targetEntry.getPlan().getId(), targetEntry.getOriginalItemId()) == 0;

            if (isSingleChunk || isLastChunk) {
                // Last or only chunk: upgrade to full task completion
                List<ScheduleEntryEntity> allTaskEntries = scheduleEntryRepository
                        .findByPlanIdAndOriginalItemId(targetEntry.getPlan().getId(), targetEntry.getOriginalItemId());
                allTaskEntries.forEach(entry -> entry.setIsCompleted(true));
                updatedEntities = scheduleEntryRepository.saveAll(allTaskEntries);

                if (targetEntry.getOriginalItemId() != null) {
                    taskRepository.findByIdAndAccountId(targetEntry.getOriginalItemId(), accountId)
                            .ifPresent(task -> {
                                task.setStatus(TaskStatus.DONE);
                                taskRepository.save(task);
                            });
                }
            } else {
                updatedEntities = List.of(targetEntry);
            }
        }

        MarkScheduleEntryResponse response = new MarkScheduleEntryResponse();
        response.setUpdatedEntries(updatedEntities.stream()
                .map(scheduleMapper::mapEntryEntityToDto)
                .toList());
        return response;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Rounds a LocalDateTime up to the next 15-minute interval.
     * Examples:
     * 10:00 -> 10:00
     * 10:01 -> 10:15
     * 10:14 -> 10:15
     * 10:15 -> 10:15
     * 10:16 -> 10:30
     *
     * @param dateTime the date-time to round
     * @return the rounded date-time
     */
    public LocalDateTime getScheduleFromDateTime(LocalDateTime dateTime) {

        LocalDateTime rounded = dateTime
                .withSecond(0)
                .withNano(0);

        int minute = rounded.getMinute();

        // Calculate next multiple of 15
        int roundedMinute = ((minute + 14) / 15) * 15;

        // Handle overflow to next hour
        if (roundedMinute == 60) {
            return rounded.plusHours(1).withMinute(0);
        }
        return rounded.withMinute(roundedMinute);
    }

    /**
     * Validate the request
     * @param jwt Token
     * @param accountId Account
     * @param fromDate Start date for schedule generation
     */
    private void validateRequest(Jwt jwt, UUID accountId, OffsetDateTime fromDate) {

        // Check if account exists
        Optional<UserAccountEntity> accountOpt = userAccountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        // Checks permissions
        if (!userIdentityService.hasUserAccessToAccount(jwt, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + jwt.getSubject() + " does not have access to account " + accountId);
        }

        // Validate fromDate (e.g. cannot be in the past)

        System.out.println(fromDate);
        OffsetDateTime utcTime = OffsetDateTime.now(ZoneOffset.UTC);
        if (fromDate.isBefore(utcTime.minusSeconds(5))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "From date cannot be in the past");
        }
    }

    /**
     * Creates a scheduling context containing tasks, working times, available time slots
     * and the scheduling start date.
     *
     * @param jwt       JWT token containing user information
     * @param accountId Account ID for which the context should be created
     * @param fromDate  Start date for the schedule
     * @return Scheduling context for the solver
     */
    public SchedulingContext createSchedulingContext(Jwt jwt, UUID accountId, LocalDateTime fromDate) {

        List<TaskResponse> allTasks = new ArrayList<>();
        for (TaskResponse task : taskService.getTasksForAccountId(jwt, accountId)) {
            if (!task.getStatus().equals(TaskStatus.DONE)) {
                allTasks.add(task);
            }
        }

        // Load working times for this account
        List<WorkingTimeEntity> workingTimes = userAccountRepository.findByIdWithWorkingTimes(accountId)
                .map(UserAccountEntity::getWorkingTimes)
                .orElse(List.of());

        if (workingTimes.isEmpty()) {
            WorkingTimeEntity defaultWorkingTimes = new WorkingTimeEntity();
            defaultWorkingTimes.setDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            defaultWorkingTimes.setStartTime(LocalTime.of(8, 0));
            defaultWorkingTimes.setEndTime(LocalTime.of(17, 0));
            workingTimes = List.of(defaultWorkingTimes);
        }

        // Get available timeslots
        List<Appointment> allAppointments = appointmentService.listAppointments(accountId, jwt).getAppointments();
        List<TimeSlot> availableSlots = getAvailableTimeSlots(allAppointments, workingTimes, fromDate);

        // Create schedule context
        return new SchedulingContext(
                fromDate, availableSlots, allTasks, allAppointments, workingTimes
        );
    }

    /**
     * Calculate a list of available time slots to plan the task chunks into.
     * @param allAppointments All appointments scheduled for this account>
     * @param workingTimes List of working time definitions for the account
     * @param scheduleFromDateTime Start date for calculating available slots
     * @return Available timeslots for multiple days starting from fromDate
     */
    public List<TimeSlot> getAvailableTimeSlots(List<Appointment> allAppointments, List<WorkingTimeEntity> workingTimes, LocalDateTime scheduleFromDateTime) {

        int nWeeks = getNWeeksAhead(scheduleFromDateTime, allAppointments);

        List<TimeSlot> availableSlots = new ArrayList<>();

        if (workingTimes.isEmpty()) {
            log.warn("No working times defined for this account");
            return availableSlots;
        }


        // Get the Monday of the week for the fromDate
        int weekDayInt = scheduleFromDateTime.getDayOfWeek().getValue();
        int daysToMonday = (weekDayInt + 6) % 7; // Calculate how many days to go back to reach Monday
        LocalDateTime currentDateTime = scheduleFromDateTime.minusDays(daysToMonday);


        // Create map (weekday -> working hour) for easier processing
        Map<DayOfWeek, TimeSlot> workingHoursPerDay = createWorkingTimeMapping(workingTimes);

        for (int i = 0; i <= nWeeks; i++) {
            // Always go from Mon - Sun
            for (DayOfWeek weekday : DayOfWeek.values()) {

                if (dateIsBeforeScheduleFrom(currentDateTime, scheduleFromDateTime)) {
                    // Skip days before scheduleFromDateTime
                    currentDateTime = currentDateTime.plusDays(1);
                    continue;
                }

                // Update time for day after fromDate
                if (isDateAfterScheduleFrom(currentDateTime, scheduleFromDateTime)) {
                    currentDateTime = currentDateTime.withHour(0).withMinute(0).withSecond(0);
                }

                if (reachedNWeeks(nWeeks, i) && reachedWeekday(currentDateTime, scheduleFromDateTime)) {
                    // Stop if we have reached the weekday on which the schedule was started after planning for nWeeks
                    break;
                }

                // Check if there are working times for this weekday
                if (workingHoursPerDay.containsKey(weekday)) {

                    // Get appointments for this specific date
                    List<Appointment> dayAppointments = getAppointmentsForDay(allAppointments, currentDateTime.toLocalDate());
                    // Merge overlapping appointments to avoid schedule generation issues
                    dayAppointments = mergeOverlappingAppointments(dayAppointments);


                    // Get working hours for this day
                    TimeSlot workingHours = workingHoursPerDay.get(weekday);
                    TimeSlot effectiveWorkingSlot = createEffectiveWorkingSlot(currentDateTime, workingHours);

                    if (effectiveWorkingSlot == null) {
                        // No time left for this day
                        currentDateTime = currentDateTime.plusDays(1);
                        continue;
                    }
                    LocalTime currentTime = effectiveWorkingSlot.startTime();
                    LocalTime workEndTime = effectiveWorkingSlot.endTime();


                    availableSlots.addAll(calculateFreeSlotsBetweenAppointments(
                        currentDateTime.toLocalDate(), dayAppointments, currentTime, workEndTime
                    ));
                }
                // Update current date
                currentDateTime = currentDateTime.plusDays(1);
            }
        }
        return availableSlots;
    }

    /**
     * Calculates free time slots between appointments.
     *
     * @param targetDate Date for which the slots are calculated
     * @param appointments Sorted appointments for the day
     * @param startTime Start of available working time
     * @return List of free slots
     */
    private List<TimeSlot> calculateFreeSlotsBetweenAppointments(
            LocalDate targetDate,
            List<Appointment> appointments,
            LocalTime startTime,
            LocalTime workEndTime
    ) {

        List<TimeSlot> freeSlots = new ArrayList<>();
        LocalTime currentTime = startTime;

        for (Appointment appointment : appointments) {

            LocalTime appointmentStart = LocalTime.parse(appointment.getStartTime());
            LocalTime appointmentEnd = LocalTime.parse(appointment.getEndTime());

            // Gap before appointment
            if (currentTime.isBefore(appointmentStart)) {
                freeSlots.add(new TimeSlot(targetDate, currentTime, appointmentStart));
            }

            // Move current time forward
            if (appointmentEnd.isAfter(currentTime)) {
                currentTime = appointmentEnd;
            }
        }

        // Add remaining time after last appointment until work end time
        if (currentTime.isBefore(workEndTime)) {
            freeSlots.add(new TimeSlot(targetDate, currentTime, workEndTime));
        }

        return freeSlots;
    }

    /**
     * Creates the effective working slot for the current day.
     * Ensures that planning on the start day only begins from the given start time.
     *
     * @param currentDateTime Current planning date/time
     * @param workingHours Configured working hours for the day
     * @return Effective working slot or null if no time is available
     */
    private TimeSlot createEffectiveWorkingSlot(LocalDateTime currentDateTime, TimeSlot workingHours) {

        LocalTime currentTime = Collections.max(
                List.of(currentDateTime.toLocalTime(), workingHours.startTime())
        );

        LocalTime workEndTime = workingHours.endTime();

        // No remaining working time available
        if (!currentTime.isBefore(workEndTime)) {
            return null;
        }

        return new TimeSlot(
                currentDateTime.toLocalDate(),
                currentTime,
                workEndTime
        );
    }

    /**
     * Checks whether the current date is the day after the scheduling start date.
     *
     * @param currentDateTime Current date/time
     * @param scheduleFromDateTime Scheduling start date/time
     * @return True if current date is the next day
     */
    private boolean isDateAfterScheduleFrom(LocalDateTime currentDateTime, LocalDateTime scheduleFromDateTime) {
        return currentDateTime.toLocalDate().isEqual(scheduleFromDateTime.toLocalDate().plusDays(1));
    }

    /**
     * Checks whether the target number of weeks has been reached.
     *
     * @param nWeeks Total number of weeks
     * @param weekNr Current week number
     * @return True if week limit was reached
     */
    private boolean reachedNWeeks(int nWeeks, int weekNr) {
        return nWeeks <= weekNr;
    }

    /**
     * Checks whether the target weekday after n weeks was reached.
     *
     * @param currentDateTime Current date/time
     * @param scheduleFromDateTime Scheduling start date/time
     * @return True if target date was reached
     */
    private boolean reachedWeekday(LocalDateTime currentDateTime, LocalDateTime scheduleFromDateTime) {
        return currentDateTime.getDayOfWeek() == scheduleFromDateTime.getDayOfWeek();
    }

    /**
     * Checks whether the current date/time is before the scheduling start.
     *
     * @param currentDateTime Current date/time
     * @param scheduleFromDateTime Scheduling start date/time
     * @return True if current date/time is before schedule start
     */
    private boolean dateIsBeforeScheduleFrom(LocalDateTime currentDateTime, LocalDateTime scheduleFromDateTime) {
        return currentDateTime.isBefore(scheduleFromDateTime);
    }

    /**
     * Creates a mapping from weekdays to working time slots.
     *
     * @param workingTimes Working time definitions
     * @return Map containing working slots per weekday
     */
    private Map<DayOfWeek, TimeSlot> createWorkingTimeMapping(List<WorkingTimeEntity> workingTimes) {
        return workingTimes.stream()
                .flatMap(wt -> wt.getDays().stream()
                        .map(day -> Map.entry(
                                day,
                                new TimeSlot(null, wt.getStartTime(), wt.getEndTime())
                        ))
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * Determine the date of the last planed appointment and plans n weeks ahead where n is the number of weeks
     *  between "fromDate" and the date of the last appointment.
     *  A minimum of weeks ahead is set to 4.
     * @param fromDate The date from which on the schedule is calculated
     * @param allAppointments All appointments for this account
     * @return Minimal 4 or the number of weeks between fromDate and the date of the last appointment
     */
    private int getNWeeksAhead(LocalDateTime fromDate, List<Appointment> allAppointments) {

        List<Appointment> appointmentsWithDate = allAppointments.stream()
                .filter(apt -> apt.getDate() != null)
                .sorted(Comparator.comparing(Appointment::getDate))
                .toList();

        if (appointmentsWithDate.isEmpty()) return 4;

        LocalDate lastDate =  appointmentsWithDate.getLast().getDate();
        int weeksBetween = (int) ChronoUnit.WEEKS.between(fromDate.toLocalDate(), lastDate) + 1;

        return Math.max(weeksBetween, 4);
    }

    /**
     * Creates an error response with a warning message
     *
     * @param errorMessage The error message to include
     * @return ScheduleResponse with error warning
     */
    private ScheduleResponse createErrorResponse(String errorMessage) {
        ScheduleResponse response = new ScheduleResponse();
        response.setWarnings(List.of(new ScheduleWarning(
                ScheduleWarning.TypeEnum.OTHER,
                errorMessage
        )));
        return response;
    }

    /**
     * This method checks whether the appointment is scheduled for this day or not.
     * In case the appointment type is ONE_TIME, the method checks the appointment date
     * In case the appointment type is RECURRING, the method uses the RRule to check if the
     *      targetDate is influenced by the recurring task.
     * @param targetDate The date to check
     * @param appointment The appointment to check
     * @return True, if the appointment is on that day, false otherwise
     */
    private boolean isAppointmentOnDate(LocalDate targetDate, Appointment appointment) {

        // One time appointment
        if (appointment.getAppointmentType() == AppointmentType.ONE_TIME) {
            return appointment.getDate().equals(targetDate);
        }

        // Recurring appointment
        String rrule = appointment.getRrule();
        if (rrule == null || rrule.isBlank()) return false;

        // Example:
        // FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
        String[] ruleParts = rrule.split(";");
        String byDayPart = null;

        for (String part : ruleParts) {
            if (part.startsWith("BYDAY=")) {
                byDayPart = part.substring("BYDAY=".length());
                break;
            }
        }

        if (byDayPart == null || byDayPart.isBlank()) return false;

        String targetDay = switch (targetDate.getDayOfWeek()) {
            case MONDAY -> "MO";
            case TUESDAY -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY -> "TH";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SU";
        };

        String[] allowedDays = byDayPart.split(",");
        for (String day : allowedDays) {
            if (day.equals(targetDay)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Merges overlapping appointments into combined blocks so that the free-slot
     * calculation in {@link #getAvailableTimeSlots} works correctly even when
     * legacy data contains overlapping appointments.
     *
     * @param sortedAppointments Appointments for a single day, already sorted by startTime
     * @return A list of synthetic Appointment objects with merged time ranges
     */
    private List<Appointment> mergeOverlappingAppointments(List<Appointment> sortedAppointments) {
        if (sortedAppointments.size() <= 1) {
            return sortedAppointments;
        }

        List<Appointment> merged = new ArrayList<>();

        final Appointment current = sortedAppointments.getFirst();
        LocalTime currentStart = LocalTime.parse(current.getStartTime());
        LocalTime currentEnd = LocalTime.parse(current.getEndTime());

        for (int i = 1; i < sortedAppointments.size(); i++) {
            Appointment next = sortedAppointments.get(i);
            LocalTime nextStart = LocalTime.parse(next.getStartTime());
            LocalTime nextEnd = LocalTime.parse(next.getEndTime());

            if (nextStart.isBefore(currentEnd) || nextStart.equals(currentEnd)) {
                // Overlapping or adjacent – extend the end time
                if (nextEnd.isAfter(currentEnd)) {
                    currentEnd = nextEnd;
                }
            } else {
                // No overlap – emit current block and start new one
                Appointment block = new Appointment();
                block.setStartTime(currentStart.toString());
                block.setEndTime(currentEnd.toString());
                merged.add(block);

                currentStart = nextStart;
                currentEnd = nextEnd;
            }
        }

        // Emit last block
        Appointment block = new Appointment();
        block.setStartTime(currentStart.toString());
        block.setEndTime(currentEnd.toString());
        merged.add(block);

        return merged;
    }

    /**
     * @param allAppointments All appointments for a specific account
     * @param target The date for which the appointments are listed
     * @return List of appointments for a given day
     */
    private List<Appointment> getAppointmentsForDay(List<Appointment> allAppointments, LocalDate target) {
        return allAppointments.stream()
                .filter(apt -> isAppointmentOnDate(target, apt))
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .toList();
    }
}
