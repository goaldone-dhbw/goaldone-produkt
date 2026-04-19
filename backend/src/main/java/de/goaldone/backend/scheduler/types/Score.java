package de.goaldone.backend.scheduler.types;

import lombok.Getter;

import java.util.ArrayList;

@Getter
public class Score {

    private int softConstraintScore;
    private int hardConstraintScore;

    public int calculateScore(ArrayList<Constraint> constraints) {
        for (Constraint constraint : constraints) {
            if (constraint instanceof HardConstaint) {

                //error
            } else {
                hardConstraintScore += constraint.getValue();
            }
        }
        return 0; //TODO
    }


}
