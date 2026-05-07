package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.*;

import java.time.LocalDateTime;
import java.util.*;

public class CPMAlgorithm {

    private final ChunkSorter chunkSorter;

    public CPMAlgorithm() {
        chunkSorter = new ChunkSorter();
    }


    /**
     * Creates initial schedule
     * @param context Scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return A first, heuristic generated schedule
     */
    SolverState generateInitialSchedule(SchedulingContext context) {

        List<TimeSlot> availableSlots = context.availableSlots();
        List<TaskChunk> chunks = context.chunks();
        // Assumption: There is more available time than needed to schedule all tasks, so we can ignore scheduling conflicts for the initial solution.


        List<TaskSlack>  taskSlacks = calculateSlack(chunks);


        // Sort chunks based on dependencies (topological order)
        List<TaskChunk> sortedChunks = chunkSorter.topologicalSort(chunks);




        for (TaskChunk chunk : sortedChunks) {
            // TODO: Schedule each chunk in available time slots
        }


        SolverState emptyResult = new SolverState(
                List.of(), // List<ScheduledTask>
                List.of()  // List<TaskSlack>
        );

        return emptyResult;

    }


    /**
     * Forward pass: compute Earliest Start (ES) and Earliest End (EF) for each task.
     * ES = max(notBefore, max(EF of predecessors))
     * EF = ES + task duration in available work minutes
     */
    List<TaskSlack> calculateSlack(List<TaskChunk> chunks) {

        ArrayList<TaskSlack> results = new ArrayList<>();

        for (TaskChunk chunk : chunks) {

            LocalDateTime earliestStart = chunk.notBefore();
            LocalDateTime earliestFinish = chunk.notBefore().plusMinutes(chunk.durationMinutes());
            LocalDateTime latestStart = chunk.deadline().minusMinutes(chunk.durationMinutes());
            LocalDateTime latestFinish = chunk.deadline();
            long slackMinutes = java.time.Duration.between(earliestStart, latestStart).toMinutes();

            results.add(new TaskSlack(
                    chunk.taskId(),
                    earliestStart,
                    earliestFinish,
                    latestStart,
                    latestFinish,
                    slackMinutes
            ));
        }
        return results;
    }
}


