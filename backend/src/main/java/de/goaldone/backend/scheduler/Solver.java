package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;

import java.util.List;

public class Solver {

    ConstraintHandler constraintHandler;
    CPMAlgorithm cpmAlgorithm;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
    }

    public SchedulingResult createSchedule(SchedulingContext context) {

        // Calculate initial schedule using CPM
        Schedule initialSchedule = this.cpmAlgorithm.generateInitialSchedule(context);

        // TODO: Call metaheuristic (MoveStrategies + Late Acceptance + Tabu Search) to improve

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
