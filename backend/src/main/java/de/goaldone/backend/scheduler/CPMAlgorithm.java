package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.scheduler.types.model.*;

import java.util.ArrayList;
import java.util.List;

public class CPMAlgorithm {

    private final ConstraintHandler constraintHandler;

    public CPMAlgorithm(ConstraintHandler constraintHandler) {
        this.constraintHandler = constraintHandler;
    }


    //TODO: @Leon Parameters, Return Types
    // heuristische bzw erste Lösung
    PlanningResult generateInitialSchedule(PlanningContext context) {


        ArrayList<ScheduleEntry> schedule = null;

        int score = constraintHandler.calculateScore(schedule);

        throw new UnsupportedOperationException("CPM-Algorithm implementation pending");

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
