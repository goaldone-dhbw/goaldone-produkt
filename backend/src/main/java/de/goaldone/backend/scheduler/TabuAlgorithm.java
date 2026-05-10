package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.MoveEvent;
import de.goaldone.backend.scheduler.types.model.MoveHistory;

public class TabuAlgorithm {

    /**
     * Validates the move by checking if it is in the tabu list (move history).
     * If it is not tabu, the move can be accepted.
     * If it is tabu, the move can still be accepted if it meets the aspiration criteria.
     * @param currentScore Score of the current best schedule
     * @param newScore Score of the new schedule
     * @param moveHistory Move history with n latest moves
     * @param latestMove The current move in question
     * @return True if move is accepted, false otherwise
     */
    boolean validateMove(int currentScore, int newScore, MoveHistory moveHistory, MoveEvent latestMove) {

        if (!moveHistory.contains(latestMove)) {
            return true; // Move is not tabu, can be accepted
        }

        return isAspiration(currentScore, newScore);
    }

    /**
     * Accept move if the new score is better than the currently best score
     * @param currentScore Score of the current best schedule
     * @param newScore Score of the new schedule in question
     * @return True if the move should be accepted, false otherwise
     */
    boolean isAspiration(int currentScore, int newScore) {
        return newScore > currentScore; // Accept move, if better score

    }
}


