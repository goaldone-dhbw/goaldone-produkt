package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.PlanningContext;
import de.goaldone.backend.scheduler.types.model.PlanningResult;
import de.goaldone.backend.scheduler.types.model.UnscheduledTaskResult;

import java.util.ArrayList;

public class Solver {

    ConstraintHandler constraintHandler;
    CPMAlgorithm cpmAlgorithm;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
    }

    public PlanningResult createSchedule(PlanningContext context) {
        // TODO: Call CPMAlgo.generateInitialSchedule for initial solution
        // TODO: Call metaheuristic (Late Acceptance + Tabu Search) to improve
        // TODO: Return final PlanningResult
        //throw new UnsupportedOperationException("Algorithm implementation pending");

        ArrayList<ScheduleEntry> initialSchedule = this.cpmAlgorithm.generateInitialSchedule(context);


        // algo Aufrufe etc.

        // schedule besteht aus ArrayList<ScheduleEntry>

        // Example data
        ArrayList<ScheduleEntry> schedule = null;
        ArrayList<UnscheduledTaskResult> unscheduled = null;
        ArrayList<ScheduleWarning> warnings = null;
        int scheduleScore = this.constraintHandler.calculateScore(schedule);

        PlanningResult bestSchedule = new PlanningResult(
            scheduleScore,schedule, unscheduled, warnings
        );

        return bestSchedule;
    }
}
