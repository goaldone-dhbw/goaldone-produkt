package de.goaldone.backend.scheduler.types;

import lombok.Getter;

import java.util.ArrayList;

@Getter
public class Score {
    private long hardConstraintScore;
    private long softConstraintScore;

    public long calculateScore(ArrayList<Constraint> constraints) {
        for (Constraint constraint : constraints) {
            if (constraint instanceof HardConstaint) {
                hardConstraintScore += constraint.getValue();
            } else {
                softConstraintScore += constraint.getValue();
            }
        }
        return hardConstraintScore + softConstraintScore;
    }

    public boolean isFeasible() {
        return hardConstraintScore == 0;
    }
}
