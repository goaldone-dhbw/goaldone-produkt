package de.goaldone.backend.scheduler;

/**
 * Implements the Late Acceptance Hill Climbing (LAHC) strategy.
 * A move is accepted if the new score is at least as good as either the
 * current score OR the score from L iterations ago (stored in a ring buffer).
 * The ring buffer is updated after every iteration, regardless of whether the move was accepted.
 */
public class LateAcceptance {

    private final int[] scoreHistory;
    private final int lateAcceptanceSize;
    private int iteration;

    /**
     * @param lateAcceptanceSize Length L of the ring buffer (number of past iterations to compare against)
     */
    public LateAcceptance(int lateAcceptanceSize) {
        this.lateAcceptanceSize = lateAcceptanceSize;
        this.scoreHistory = new int[lateAcceptanceSize];
        this.iteration = 0;
    }

    /**
     * Pre-fills the entire ring buffer with the initial score before the solver loop starts.
     * @param initialScore Score of the initial solution
     */
    public void initialize(int initialScore) {
        for (int i = 0; i < lateAcceptanceSize; i++) {
            scoreHistory[i] = initialScore;
        }
        iteration = 0;
    }

    /**
     * Checks whether a move should be accepted under the Late Acceptance criterion.
     * Accepts if: newScore >= currentScore (direct improvement)
     *          OR newScore >= scoreHistory[iteration % L] (better than L iterations ago)
     * @param newScore     Score of the candidate (new) solution
     * @param currentScore Score of the currently accepted solution
     * @return True if the move should be accepted, false otherwise
     */
    public boolean validateMove(int newScore, int currentScore) {
        int lateScore = scoreHistory[iteration % lateAcceptanceSize];
        return newScore >= currentScore || newScore >= lateScore;
    }

    /**
     * Updates the ring buffer with the score of the solution accepted in this iteration.
     * Must be called once per iteration (even when the move was rejected – pass currentScore in that case).
     * @param score Score to store at the current ring buffer position
     */
    public void updateHistory(int score) {
        scoreHistory[iteration % lateAcceptanceSize] = score;
        iteration++;
    }
}
