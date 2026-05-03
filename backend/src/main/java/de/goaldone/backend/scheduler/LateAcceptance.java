package de.goaldone.backend.scheduler;

public class LateAcceptance {

    private final ConstraintHandler constraintHandler;

    public LateAcceptance(ConstraintHandler constraintHandler) {
        this.constraintHandler = constraintHandler;
    }
    boolean validateMove(int candidateScore, int lateScore) {
        return false;
    }
}
