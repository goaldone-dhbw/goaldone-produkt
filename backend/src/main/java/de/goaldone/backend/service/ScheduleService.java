package de.goaldone.backend.service;

import de.goaldone.backend.model.GenerateScheduleRequest;
import de.goaldone.backend.model.ScheduleResponse;
import de.goaldone.backend.model.TaskListResponse;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    Solver solver = new Solver();
    TaskService taskService = new TaskService();

    private final Executor executor = Executors.newFixedThreadPool(5);

    public List<ScheduleResponse> generateMultiAccountSchedule(
            List<UUID> accountIds,
            GenerateScheduleRequest request,
            long timeoutMilliseconds) {

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


    public ScheduleResponse generateSchedule(UUID accountId, GenerateScheduleRequest generateScheduleRequest) {

        // Tasks
        TaskListResponse allTasks = taskService.listTasks(accountId);

        // Chunk unpinned tasks
        List<TaskChunk> chunks = null;

        // List pinned tasks
        List<ScheduledChunk> pinnedChunks = null;

        // Calculate free slots
        List<TimeSlot> availableSlots = null;

        // From which date on, should the tasks be loaded
        LocalDate fromDate = generateScheduleRequest.getFrom();

        PlanningContext planningContext = new PlanningContext(
                accountId,
                fromDate,
                availableSlots,
                chunks,
                pinnedChunks
        );

        // Forward to schedule generator
        PlanningResult bestResult = solver.createSchedule(planningContext); //TODO: Pass tasks and appointments from db

        return null;
    }

    public ScheduleResponse getSchedule() {
        return null; //TODO
    }



}