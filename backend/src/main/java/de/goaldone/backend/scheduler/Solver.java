package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.*;
import de.goaldone.backend.scheduler.types.moves.MoveSelector;

public class Solver {

    private final CPMAlgorithm cpmAlgorithm;
    private final TabuAlgorithm tabuAlgorithm;
    private final LateAcceptance lateAcceptance;
    private final MoveSelector moveSelector;
    private final MoveHistory moveHistory;

    public Solver() {
        ConstraintHandler constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
        this.tabuAlgorithm = new TabuAlgorithm(constraintHandler);
        this.lateAcceptance = new LateAcceptance(constraintHandler);
        this.moveSelector = new MoveSelector();
        this.moveHistory = new MoveHistory();
    }

    /**
     * Creates an initial schedule and multiple variants and returns the one with the best score.
     * @param context The scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return The best schedule
     */
    public SchedulingResult createSchedule(SchedulingContext context, long timeout) {

        long endTime = System.currentTimeMillis() + timeout - 500; // Subtract a small buffer to ensure we return before the timeout expires

        SolverState currentBest = this.cpmAlgorithm.generateInitialSchedule(context);

        while (System.currentTimeMillis() < endTime) {
            SolverState newState = moveSelector.selectAndApply(currentBest);

            MoveEvent latestMove = moveSelector.getLastMoveEvent();
            boolean moveAccepted = tabuAlgorithm.validateMove(currentBest, newState, moveHistory, latestMove);

            boolean lateAcceptance = this.lateAcceptance.validateMove(0,0); //TODO

            if (moveAccepted || lateAcceptance) {
                currentBest = newState;
                moveHistory.addMoveEvent(latestMove);
            }
        }

        // TODO: Convert currentBest to SchedulingResult and return it
        return null;

    }
}
