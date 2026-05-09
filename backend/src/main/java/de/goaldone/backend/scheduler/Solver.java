package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.*;
import de.goaldone.backend.scheduler.types.moves.MoveSelector;

public class Solver {

    private static final int DEFAULT_LATE_ACCEPTANCE_SIZE = 400;

    private final ConstraintHandler constraintHandler;
    private final MoveSelector moveSelector;
    private final CPMAlgorithm cpmAlgorithm;
    private final TabuAlgorithm tabuAlgorithm;
    private final LateAcceptance lateAcceptance;
    private final MoveHistory moveHistory;

    public Solver() {
        this(DEFAULT_LATE_ACCEPTANCE_SIZE);
    }

    /**
     * @param lateAcceptanceSize Length L of the Late Acceptance ring buffer
     */
    public Solver(int lateAcceptanceSize) {
        this.constraintHandler = new ConstraintHandler();
        this.cpmAlgorithm = new CPMAlgorithm();
        this.tabuAlgorithm = new TabuAlgorithm();
        this.lateAcceptance = new LateAcceptance(lateAcceptanceSize);
        this.moveSelector = new MoveSelector();
        this.moveHistory = new MoveHistory();
    }

    /**
     * Creates an initial schedule and multiple variants and returns the one with the best score.
     * @param context The scheduling context containing chunked tasks, free time slots, and the scheduling start date.
     * @return The best schedule
     */
    public SchedulingResult createSchedule(SchedulingContext context, long timeoutMs) {

        long endTime = System.currentTimeMillis() + timeoutMs - 500; // Subtract a small buffer to ensure we return before the timeout expires

        SolverState currentBest = this.cpmAlgorithm.generateInitialSchedule(context);
        int currentScore = constraintHandler.calculateScore(currentBest);
        lateAcceptance.initialize(currentScore);

        while (System.currentTimeMillis() < endTime) {

            SolverState newState = moveSelector.selectAndApply(currentBest);
            int newScore = constraintHandler.calculateScore(newState);

            MoveEvent latestMove = moveSelector.getLastMoveEvent();

            // Tabu check: reject tabu moves that don't meet aspiration criteria
            boolean tabuAccepted = tabuAlgorithm.validateMove(currentScore, newScore, moveHistory, latestMove);
            if (!tabuAccepted) {
                lateAcceptance.updateHistory(currentScore);
                continue;
            }

            // Late Acceptance check
            if (lateAcceptance.validateMove(newScore, currentScore)) {
                currentBest = newState;
                currentScore = newScore;
                moveHistory.addMoveEvent(latestMove);
            }

        return new SchedulingResult(
                constraintHandler.calculateScore(currentBest),
                currentBest,
                constraintHandler.getWarnings(currentBest)
        );
    }
}
