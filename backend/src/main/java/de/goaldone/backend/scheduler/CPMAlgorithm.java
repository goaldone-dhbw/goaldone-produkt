package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.scheduler.types.model.*;

import java.util.ArrayList;
import java.util.List;

public class CPMAlgorithm {

    ArrayList<ScheduleEntry> generateInitialSchedule(PlanningContext context) {

        // TODO: Implement CPM

        ArrayList<ScheduleEntry> schedule = null;
        return schedule;
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
