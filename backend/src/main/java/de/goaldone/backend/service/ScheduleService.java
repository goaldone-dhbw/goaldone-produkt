package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.exception.ScheduleGenerationException;
import de.goaldone.backend.model.*;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.scheduler.Chunker;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    Solver solver = new Solver();
    Chunker chunker = new Chunker();

    private final TasksService taskService;
    private final AppointmentService appointmentService;
    private final UserAccountRepository userAccountRepository;


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
                        () -> generateSchedule(jwt, accountId, request), executor
                    )
                    // If task takes too long -> complete with error response
                    .completeOnTimeout(createErrorResponse("Schedule generation timed out"), timeoutMilliseconds, TimeUnit.MILLISECONDS) //TODO: adjust timeout

                    // Handle exceptions per task (prevents whole pipeline from failing)
                    .exceptionally(ex -> {
                        log.error("Error generating schedule: {}", ex.getMessage(), ex);
                        return createErrorResponse("Schedule generation failed: " + ex.getMessage());
                    })
                )
                .toList();

            // Collect results
            return futures.stream()
                    .map(CompletableFuture::join)   // non-blocking due to completeOnTimeout
                    .filter(Objects::nonNull)       // remove timed-out or failed tasks
                    .toList();
        } catch (Exception e) {
            log.error("Failed to initialize account scheduling", e);
            throw new ScheduleGenerationException("Failed to initialize account scheduling", e);
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

        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {

            // Generate scheduling
            return CompletableFuture.supplyAsync(
                    () -> generateSchedule(jwt, accountId, generateScheduleRequest), executor
                )
                // If task takes too long -> complete with error response
                .completeOnTimeout(createErrorResponse("Schedule generation timed out"), timeoutMilliseconds, TimeUnit.MILLISECONDS)
                // Handle exceptions
                .exceptionally(ex -> {
                    log.error("Error generating schedule for account {}: {}", accountId, ex.getMessage(), ex);
                    return createErrorResponse("Schedule generation failed: " + ex.getMessage());
                })
                .join();
        } catch (Exception e) {
            log.error("Failed to initialize account scheduling", e);
            throw new ScheduleGenerationException("Failed to initialize account scheduling", e);
        }
    }

    /**
     * Orders the generation of a scheduling for an account
     *
     * @param accountId               Specific account
     * @param generateScheduleRequest Request
     * @return Response containing
     * - the generated schedule,
     * - the scheduleScore,
     * - constraint warnings
     */
    private ScheduleResponse generateSchedule(Jwt jwt, UUID accountId, GenerateScheduleRequest generateScheduleRequest) {

        validateRequest(jwt, accountId, generateScheduleRequest);

        List<TaskResponse> allTasks = taskService.getTasksForAccountId(jwt, accountId);

        // Load working times for this account
        List<WorkingTimeEntity> workingTimes = userAccountRepository.findById(accountId)
                .map(UserAccountEntity::getWorkingTimes)
                .orElse(List.of());

        List<TaskChunk> chunks = chunker.chunkTasks(allTasks, workingTimes);

        // Get available timeslots
        List<TimeSlot> availableSlots = getAvailableTimeSlots(accountId);

        // Get schedule start date
        LocalDate fromDate = generateScheduleRequest.getFrom();

        // Create schedule context
        SchedulingContext schedulingContext = new SchedulingContext(
                fromDate, availableSlots, chunks
        );

        // Forward to schedule generator
        SchedulingResult bestResult = solver.createSchedule(schedulingContext);

        // TODO: Map result to ScheduleResponse and return
        return null;
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
     * @param accountId Specific account
     * @return Available timeslots between appointments
     */
    private List<TimeSlot> getAvailableTimeSlots(UUID accountId) {
        List<Appointment> allAppointments = appointmentService.listAppointments(accountId).getAppointments();

        //TODO: calculate free time slots based on appointments and working hours

        List<TimeSlot> availableSlots = null;
        return availableSlots;
    }

    private void validateRequest(Jwt jwt, UUID accountId, GenerateScheduleRequest generateScheduleRequest) {
        // Check if account exists
        Optional<UserAccountEntity> accountOpt = userAccountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }

        // Check if user has access to this account
        String userId = jwt.getSubject();
        UserAccountEntity account = accountOpt.get();
        if (!account.getZitadelSub().equals(userId)) {
            throw new SecurityException("User " + userId + " does not have access to account " + accountId);
        }

        // Validate fromDate (e.g. cannot be in the past)
        if (generateScheduleRequest.getFrom().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("From date cannot be in the past");
        }
    }

    public ScheduleResponse getSchedule() {
        return null; //TODO
    }


}