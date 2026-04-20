package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.PlanningContext;
import de.goaldone.backend.scheduler.types.model.PlanningResult;

public class GoaldoneSolver implements Solver {

    @Override
    public PlanningResult createSchedule(PlanningContext context) {
        // TODO: Call CPMAlgo.generateInitialSchedule for initial solution
        // TODO: Call metaheuristic (Late Acceptance + Tabu Search) to improve
        // TODO: Return final PlanningResult
        throw new UnsupportedOperationException("Algorithm implementation pending");
    }
}
