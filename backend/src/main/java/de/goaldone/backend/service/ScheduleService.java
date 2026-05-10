package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.ScheduleMapper;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    Solver solver = new Solver();

    private final TasksService taskService;
    private final AppointmentService appointmentService;
    private final UserAccountRepository userAccountRepository;
    private final @Lazy UserIdentityService userIdentityService;
    private final ScheduleMapper scheduleMapper = new ScheduleMapper();

    /**
     * Generates schedules for multiple accounts asynchronously
     *
     * @param accountIds          List of accounts
     * @param request             Request parameters (e.g. fromDate)
     * @param timeoutMilliseconds Maximum time to wait for each account's schedule
     *                            generation before giving up (in milliseconds)
     * @return A schedule for each account summed up in a lCist
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
    public ScheduleResponse generateSingleAccountSchedule(
            Jwt jwt,
            UUID accountId,
            GenerateScheduleRequest generateScheduleRequest,
            long timeoutMilliseconds) {

        try  {
            return generateSchedule(jwt, accountId, generateScheduleRequest.getFrom(), timeoutMilliseconds);
        } catch (Exception e) {
            return createErrorResponse("Schedule generation failed: " + e.getMessage());
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
    public ScheduleResponse generateSchedule(Jwt jwt, UUID accountId, LocalDate fromDate, long timeoutMs) {

        validateRequest(jwt, accountId, fromDate);
        SchedulingContext schedulingContext = createSchedulingContext(jwt, accountId, fromDate, 1);
        SchedulingResult bestResult = solver.createSchedule(schedulingContext, timeoutMs);

        return this.scheduleMapper.mapToScheduleResult(
                accountId, schedulingContext, bestResult
        );
    }


    /**
     * Validate the request
     * @param jwt Token
     * @param accountId Account
     * @param fromDate Start date for schedule generation
     */
    private void validateRequest(Jwt jwt, UUID accountId, LocalDate fromDate) {

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
        if (fromDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "From date cannot be in the past");
        }
    }

    /**
     * Creates a scheduling context containing tasks, free time slots and the scheduling start date
     * @param jwt Token
     * @param accountId Account
     * @param fromDate Start date for the schedule
     * @return Scheduling context for the solver
     */
    public SchedulingContext createSchedulingContext(Jwt jwt, UUID accountId, LocalDate fromDate, int weeks) {

        List<TaskResponse> allTasks = taskService.getTasksForAccountId(jwt, accountId);

        // Load working times for this account
        List<WorkingTimeEntity> workingTimes = userAccountRepository.findById(accountId)
                .map(UserAccountEntity::getWorkingTimes)
                .orElse(List.of());

        if (workingTimes.isEmpty()) {
            WorkingTimeEntity defaultWorkingTimes = new WorkingTimeEntity();
            defaultWorkingTimes.setDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            defaultWorkingTimes.setStartTime(LocalTime.of(8, 0));
            defaultWorkingTimes.setEndTime(LocalTime.of(17, 0));
            workingTimes.add(defaultWorkingTimes);
        }

        // Get available timeslots
        List<TimeSlot> availableSlots = getAvailableTimeSlots(accountId, jwt, workingTimes, fromDate, weeks);

        // Create schedule context
        return new SchedulingContext(
                fromDate, availableSlots, allTasks, workingTimes
        );
    }

    /**
     * Calculate a list of available time slots to plan the task chunks into
     * @param accountId Specific account
     * @param workingTimes List of working time definitions for the account
     * @param fromDate Start date for calculating available slots
     * @param nWeeks Plan for N   nWeeks ahead
     * @return Available timeslots for multiple days starting from fromDate
     */
    private List<TimeSlot> getAvailableTimeSlots(UUID accountId, Jwt jwt, List<WorkingTimeEntity> workingTimes, LocalDate fromDate, int nWeeks) {
        List<TimeSlot> availableSlots = new ArrayList<>();

        if (workingTimes.isEmpty()) {
            log.warn("No working times defined for account {}", accountId);
            return availableSlots;
        }

        List<Appointment> allAppointments = appointmentService.listAppointments(accountId, jwt).getAppointments();

        // Get the Monday of the week for the fromDate
        int weekDayInt = fromDate.getDayOfWeek().getValue();
        int daysToMonday = (weekDayInt + 6) % 7; // Calculate how many days to go back to reach Monday
        LocalDate currentDate = fromDate.minusDays(daysToMonday);


        // Create map (weekday -> working hour) for easier processing
        Map<DayOfWeek, TimeSlot> mapping = workingTimes.stream()
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


        // Run for nWeeks
        /*
         * Planning logic for n weeks starting from a specific weekday:
         * Example: Start on Tuesday, plan for 4 weeks
         *
         * Week 0: Tue - Sun (skip days before start date)
         * Week 1: Mon - Sun
         * Week 2: Mon - Sun
         * Week 3: Mon - Sun
         * Week 4: Mon - Tue (stop on the same weekday after n weeks)
         */
        for (int i = 0; i <= nWeeks; i++) {
            // Always go from Mon - Sun
            for (DayOfWeek weekday : DayOfWeek.values()) {

                if (i == 0 && currentDate.isBefore(fromDate)) {
                    // Skip days before fromDate in the first week
                    currentDate = currentDate.plusDays(1);
                    continue;
                }

                if (i == nWeeks && (currentDate.isAfter(fromDate.plusWeeks(nWeeks)) || currentDate.isEqual(fromDate.plusWeeks(nWeeks)))) {
                    // Stop if we have reached the weekday on which the schedule was started after planning for nWeeks
                    break;
                }

                // Check if there are working times for this weekday
                if (mapping.containsKey(weekday)) {

                    // Get appointments for this specific date
                    List<Appointment> dayAppointments = getAppointmentsForDay(allAppointments, currentDate);

                    TimeSlot workingHours = mapping.get(weekday);

                    // Calculate free slots between appointments
                    LocalTime currentTime = workingHours.startTime();
                    LocalTime workEndTime = workingHours.endTime();

                    for (Appointment appointment : dayAppointments) {
                        LocalTime appointmentStart = LocalTime.parse(appointment.getStartTime());
                        LocalTime appointmentEnd = LocalTime.parse(appointment.getEndTime());

                        // If there's a gap before the appointment
                        if (currentTime.isBefore(appointmentStart)) {
                            availableSlots.add(new TimeSlot(currentDate, currentTime, appointmentStart));
                        }

                        // Move current time past this appointment
                        if (appointmentEnd.isAfter(currentTime)) {
                            currentTime = appointmentEnd;
                        }
                    }

                    // Add remaining time after last appointment until work end time
                    if (currentTime.isBefore(workEndTime)) {
                        availableSlots.add(new TimeSlot(currentDate, currentTime, workEndTime));
                    }
                }
                // Update current
                currentDate = currentDate.plusDays(1);
            }
        }

        log.debug("Found {} available time slots for account {} from {} for {} nWeeks",
                availableSlots.size(), accountId, fromDate, nWeeks);
        return availableSlots;
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
     *
     * @param allAppointments All appointments for a specific account
     * @param target The date for which the appointments are listed
     * @return List of appointments for a given day
     */
    private List<Appointment> getAppointmentsForDay(List<Appointment> allAppointments, LocalDate target) {
        return allAppointments.stream()
                .filter(apt -> apt.getDate().equals(target))
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .toList();
    }
}
