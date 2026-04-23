package de.goaldone.backend.service;

import de.goaldone.backend.model.*;
import de.goaldone.backend.scheduler.Chunker;
import de.goaldone.backend.scheduler.Solver;
import de.goaldone.backend.scheduler.types.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    Solver solver = new Solver();
    Chunker chunker = new Chunker();

    TaskService taskService = new TaskService();
    AppointmentService appointmentService = new AppointmentService();

    public List<ScheduleResponse> generateMultiAccountSchedule(
            List<UUID> accountIds,
            GenerateScheduleRequest request,
            long timeoutMilliseconds) {

        Executor executor = Executors.newFixedThreadPool(accountIds.size());

        // Create async tasks for each account
        List<CompletableFuture<ScheduleResponse>> futures = accountIds.stream()
            .map(accountId ->
                CompletableFuture.supplyAsync(
                        () -> generateSchedule(accountId, request), executor
                    )
                    // If task takes too long → complete with null instead of blocking
                    .completeOnTimeout(null, timeoutMilliseconds, TimeUnit.MILLISECONDS)

                    // Handle exceptions per task (prevents whole pipeline from failing)
                    .exceptionally(ex -> {
                        // log error if needed
                        System.err.println("Error processing account " + accountId + ": " + ex.getMessage());
                        return null;
                    })
            )
            .toList();

        /*
         * At this point:
         * - All tasks are running in parallel
         * - Each task has its own timeout
         * - No global blocking is required
         */

        // Collect results (JOIN is safe here because each future is guaranteed to complete)
        List<ScheduleResponse> results = futures.stream()
                .map(CompletableFuture::join)   // non-blocking due to completeOnTimeout
                .filter(Objects::nonNull)       // remove timed-out or failed tasks
                .toList();

        return results;
    }

    private List<TimeSlot> getAvailableTimeSlots(UUID accountId) {
        List<Appointment> allAppointments = appointmentService.listAppointments(accountId).getAppointments();


        //TODO: calculate free time slots based on appointments and working hours


        List<TimeSlot> availableSlots = null;
        return availableSlots;
    }

    public ScheduleResponse generateSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) {

        // Get data
        List<Task> allTasks = taskService.listTasks(accountId).getTasks();

        // Chunk tasks
        List<TaskChunk> chunks = chunker.chunkTasks(allTasks);

        // Get available timeslots
        List<TimeSlot> availableSlots = getAvailableTimeSlots(accountId);

        // Get schedule start date
        LocalDate fromDate = generateScheduleRequest.getFrom();

        // Create schedule context
        SchedulingContext schedulingContext = new SchedulingContext(
                accountId, fromDate, availableSlots, chunks
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