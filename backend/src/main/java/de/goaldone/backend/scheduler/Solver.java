package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SchedulingContext;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.moves.MoveSelector;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class Solver {

    private final ConstraintHandler constraintHandler;
    private final CPMAlgorithm cpmAlgorithm;
    private final TabuAlgorithm tabuAlgorithm;
    private final MoveSelector moveSelector;

    public Solver() {
        this.constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
        this.tabuAlgorithm = new TabuAlgorithm();
        this.moveSelector = new MoveSelector();
    }

    /**
     * Creates an initial schedule and multiple variants and returns the one with the best score.
     * @param context The scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return The best schedule
     */
    public SchedulingResult createSchedule(SchedulingContext context, long timeout) {

        // Calculate initial schedule using CPM
        SolverState initialSchedule = this.cpmAlgorithm.generateInitialSchedule(context);

        SolverState currentSchedule = initialSchedule;


        long endTime = System.currentTimeMillis() + timeout - 500; // Subtract a small buffer to ensure we return before the timeout expires

        while (System.currentTimeMillis() < endTime) {
            SolverState newState = moveSelector.selectAndApply(currentSchedule);

            currentSchedule = checkMove(currentSchedule, newState);

        }

        // Example data
        Schedule schedule = new Schedule();
        // TODO: Generate variants: Call metaheuristic (MoveStrategies + Late Acceptance + Tabu Search) to improve

        return null;

    }

    private SolverState checkMove(SolverState currentState, SolverState newState) {




        int newScore = constraintHandler.calculateScore(currentState);
        int currentScore = constraintHandler.calculateScore(newState);

        if (newScore > currentScore) {
            return newState;
        } else {
            return currentState;
        }
    }
}
