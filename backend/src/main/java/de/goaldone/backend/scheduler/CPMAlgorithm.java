package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.*;

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

        // Sort chunks based on dependencies (topological order)
        List<TaskChunk> sortedChunks = chunkSorter.topologicalSort(chunks);

        // Assumption: There is more available time than needed to schedule all tasks, so we can ignore scheduling conflicts for the initial solution.

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
     * Orchestrates forwardPass + backwardPass, computes final slackMinutes = LS − ES.
     * Returns one TaskSlack per unique taskId in chunks.
     */
    List<TaskSlack> calculateSlack(List<TaskChunk> chunks, List<TimeSlot> availableSlots) {
        return null;
    }

    /**
     * Forward pass: compute Earliest Start (ES) and Earliest End (EF) for each task.
     * ES = max(notBefore, max(EF of predecessors))
     * EF = ES + task duration in available work minutes
     */
    List<TaskSlack> forwardPass(List<TaskChunk> chunks, List<TimeSlot> availableSlots) {
        return null;
    }

    /**
     * Backward pass: compute Latest End (LF) and Latest Start (LS) for each task.
     * LF = deadline (for tasks with a deadline), else derived from project end
     * LS = LF − task duration in available work minutes
     * Requires forwardPassResults to determine the project end for tasks without deadlines.
     */
    List<TaskSlack> backwardPass(List<TaskChunk> chunks, List<TimeSlot> availableSlots,
                                 List<TaskSlack> forwardPassResults) {
        return null;
    }
}
