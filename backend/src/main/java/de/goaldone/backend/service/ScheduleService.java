package de.goaldone.backend.service;

import de.goaldone.backend.model.*;
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
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final Solver solver = new Solver();
    private final Chunker chunker = new Chunker();

    private final TasksService taskService;
    private final AppointmentService appointmentService;


    /**
     * Generates schedules for multiple accounts asynchronously within an organization.
     *
     * @param jwt                 The user's JWT.
     * @param accountIds          List of account IDs.
     * @param request             The schedule generation request.
     * @param xOrgID              The organization ID context.
     * @param timeoutMilliseconds Maximum wait time per account.
     * @return A list of generated schedules.
     */
    public List<ScheduleResponse> generateMultiAccountSchedule(
            Jwt jwt,
            List<UUID> accountIds,
            GenerateScheduleRequest request,
            UUID xOrgID,
            long timeoutMilliseconds) {

        if (accountIds.isEmpty()) {
            return List.of();
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(accountIds.size())) {
            List<CompletableFuture<ScheduleResponse>> futures = accountIds.stream()
                    .map(accountId ->
                            CompletableFuture.supplyAsync(
                                            () -> generateSchedule(jwt, accountId, request, xOrgID), executor
                                    )
                                    .completeOnTimeout(null, timeoutMilliseconds, TimeUnit.MILLISECONDS)
                                    .exceptionally(ex -> {
                                        log.error("Error processing account {}: {}", accountId, ex.getMessage());
                                        return null;
                                    })
                    )
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create executor for account scheduling", e);
        }
    }

    /**
     * Retrieves available time slots for a specific account.
     *
     * @param accountId The account ID.
     * @param xOrgID    The organization ID context.
     * @return A list of available time slots.
     */
    private List<TimeSlot> getAvailableTimeSlots(UUID accountId, UUID xOrgID) {
        // TODO: Pass xOrgID to appointmentService when implemented
        List<Appointment> allAppointments = appointmentService.listAppointments(xOrgID, accountId).getAppointments();

        //TODO: calculate free time slots based on appointments and working hours
        return null;
    }


    /**
     * Generates a schedule for a single account within an organization.
     *
     * @param jwt                     The user's JWT.
     * @param accountId               The account ID.
     * @param generateScheduleRequest The schedule generation request.
     * @param xOrgID                  The organization ID context.
     * @return The generated schedule response.
     */
    public ScheduleResponse generateSchedule(Jwt jwt, UUID accountId, GenerateScheduleRequest generateScheduleRequest, UUID xOrgID) {

        // Get data
        List<TaskResponse> allTasks = taskService.getTasksForAccountId(jwt, accountId, xOrgID);

        // Chunk tasks
        List<TaskChunk> chunks = chunker.chunkTasks(allTasks);

        // Get available timeslots
        List<TimeSlot> availableSlots = getAvailableTimeSlots(accountId, xOrgID);

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

    public ScheduleResponse getSchedule() {
        return null; //TODO
    }


}