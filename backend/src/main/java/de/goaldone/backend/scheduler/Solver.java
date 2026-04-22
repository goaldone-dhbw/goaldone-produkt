package de.goaldone.backend.scheduler;

public class Solver {

    ConstraintHandler constraintHandler;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
    }


    //TODO: @Leon Parameters, Return Types
    public void createSchedule() {
        // CPMAlgo.generateInitialSchedule
        // algo Aufrufe etc.

        // schedule besteht aus ArrayList<ScheduleEntry>

        int scheduleScore = this.constraintHandler.calculateScore(null);

    }

    public PlanningResult createSchedule(PlanningContext context) {
        // TODO: Call CPMAlgo.generateInitialSchedule for initial solution
        // TODO: Call metaheuristic (Late Acceptance + Tabu Search) to improve
        // TODO: Return final PlanningResult
        throw new UnsupportedOperationException("Algorithm implementation pending");
    }
}
