package de.goaldone.backend.service;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
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
            return List.of();
        }

        // Generate schedules in parallel
        try (ExecutorService executor = Executors.newFixedThreadPool(accountIds.size())) {
            // Create async tasks for each account with index tracking
            List<CompletableFuture<ScheduleResponse>> futures = accountIds.stream()
                .map(accountId ->
                    CompletableFuture.supplyAsync(
                        () -> generateSchedule(jwt, accountId, request), executor
                    )
                    // If task takes too long -> complete with error response
                    .completeOnTimeout(createErrorResponse("Schedule generation timed out"), timeoutMilliseconds, TimeUnit.MILLISECONDS)

                    // Handle exceptions per task (prevents whole pipeline from failing)
                    .exceptionally(ex -> {
                        log.error("Error generating schedule: {}", ex.getMessage(), ex);
                        return createErrorResponse("Schedule generation failed: " + ex.getMessage());
                    })
                )
                .toList();

            /*
             * At this point:
             * - All tasks are running in parallel
             * - Each task has its own timeout
             * - No global blocking is required
             */

            // Collect results
            return futures.stream()
                .map(CompletableFuture::join)
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create executor for account scheduling", e);
        }
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


    /**
     * Generates schedule for a single account with exception handling
     *
     * @param jwt                     JWT token containing user information
     * @param accountId               Specific account
     * @param generateScheduleRequest Request parameters
     * @return ScheduleResponse with schedule or error warnings
     */
    public ScheduleResponse generateSingleAccountSchedule(
            Jwt jwt,
            UUID accountId,
            GenerateScheduleRequest generateScheduleRequest) {

        try {
            return generateSchedule(jwt, accountId, generateScheduleRequest);
        } catch (Exception ex) {
            log.error("Error generating schedule for account {}: {}", accountId, ex.getMessage(), ex);
            return createErrorResponse("Schedule generation failed: " + ex.getMessage());
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
    public ScheduleResponse generateSchedule(Jwt jwt, UUID accountId, GenerateScheduleRequest generateScheduleRequest) {

        // Get data
        List<TaskResponse> allTasks = taskService.getTasksForAccountId(jwt, accountId);

        // Load working times for this account
        List<WorkingTimeEntity> workingTimes = userAccountRepository.findById(accountId)
                .map(UserAccountEntity::getWorkingTimes)
                .orElse(List.of());

        // Chunk tasks
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
                ScheduleWarning.TypeEnum.UNKNOWN,
                errorMessage
        )));
        return response;
    }

    public ScheduleResponse getSchedule() {
        return null; //TODO
    }


}