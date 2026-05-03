package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.MoveEvent;
import de.goaldone.backend.scheduler.types.model.MoveHistory;
import de.goaldone.backend.scheduler.types.model.SolverState;

public class TabuAlgorithm {

    private final ConstraintHandler constraintHandler;

    public TabuAlgorithm(ConstraintHandler constraintHandler) {
        this.constraintHandler = constraintHandler;
    }

    /**
     * Validates the move by checking if it is in the tabu list (move history).
     * If it is not tabu, the move can be accepted.
     * If it is tabu, the move can still be accepted if it meets the aspiration criteria.
     * @param currentBest Currently best schedule
     * @param newState New schedule
     * @param moveHistory Move history with n latest moves
     * @param latestMove The current move in question
     * @return True if move is accepted, false otherwise
     */
    boolean validateMove(SolverState currentBest, SolverState newState, MoveHistory moveHistory, MoveEvent latestMove) {

        if (!moveHistory.contains(latestMove)) {
            return true; // Move is not tabu, can be accepted
        }

        return isAspiration(currentBest, newState);
    }

    /**
     * Accept move if the new score is better than the currently best score
     * @param currentBest Currently best schedule
     * @param newState New schedule in question
     * @return True if the move should be accepted, false otherwise
     */
    boolean isAspiration(SolverState currentBest, SolverState newState) {
        int newScore = constraintHandler.calculateScore(currentBest);
        int currentScore = constraintHandler.calculateScore(newState);

        return newScore > currentScore; // Accept move, if better score

    }
}


