package de.goaldone.backend.scheduler.types;

import lombok.Getter;

import java.util.ArrayList;

@Getter
public class Score {

    private int softConstraintScore;
    private int hardConstraintScore;

    private final int invalidScheduleScore = -1000;

    public int calculateScore(ArrayList<HardConstraint> hardConstraints, ArrayList<SoftConstraint> softConstraints) {

        // Check if any hard constraint is violated
        for (HardConstraint hardConstraint : hardConstraints) {
            if (hardConstraint.isViolated) {
                return invalidScheduleScore;
            }
        }

        // Calculate the total score based on soft constraints
        int totalSoftConstraintScore = 0;
        for (SoftConstraint softConstraint : softConstraints) {
            if (softConstraint.isActive) {
                totalSoftConstraintScore += softConstraint.getValue();
            }
        }
        this.softConstraintScore = totalSoftConstraintScore;



        return 0;
    }


}
