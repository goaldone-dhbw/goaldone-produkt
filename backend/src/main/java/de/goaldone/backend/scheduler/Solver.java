package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;

import java.util.List;

public class Solver {

    private final ConstraintHandler constraintHandler;
    private final CPMAlgorithm cpmAlgorithm;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
    }

    /**
     * Creates an initial schedule and multiple variants and returns the one with the best score.
     * @param context The scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return The best schedule
     */
    public SchedulingResult createSchedule(SchedulingContext context) {

        // Calculate initial schedule using CPM
        Schedule initialSchedule = this.cpmAlgorithm.generateInitialSchedule(context);

        // TODO: Generate variants (MoveStrategies + Late Acceptance + Tabu Search)

        // Example data
        Schedule schedule = new Schedule();

        // Collect warnings for violated soft constraints
        List<ScheduleWarning> warnings = constraintHandler.getWarnings(schedule);

        // Calculate the score for the scheduling
        int scheduleScore = this.constraintHandler.calculateScore(schedule);

        SchedulingResult bestSchedule = new SchedulingResult(
                scheduleScore, schedule, warnings
        );

        return bestSchedule;
    }
}
