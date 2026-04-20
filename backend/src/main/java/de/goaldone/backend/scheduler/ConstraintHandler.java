package de.goaldone.backend.scheduler;

import de.goaldone.backend.exception.InvalidScheduleException;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.constraints.DeadlineConstraint;
import de.goaldone.backend.scheduler.types.constraints.PauseAfterReachedCognitiveLoadConstraint;

import java.util.ArrayList;

public class ConstraintHandler {

    ArrayList<SoftConstraint> softConstraints;
    ArrayList<HardConstraint> hardConstraints;

    /**
     * Initializes all constraints and adds them to an arraylist
     */
    public ConstraintHandler() {
        this.softConstraints = new ArrayList<>();
        this.hardConstraints = new ArrayList<>();

        // Hard constraints
        DeadlineConstraint deadlineConstraint = new DeadlineConstraint();

        this.hardConstraints.add(deadlineConstraint);


        // Soft constraints
        PauseAfterReachedCognitiveLoadConstraint pauseAfterReachedCognitiveLoadConstraint =
                new PauseAfterReachedCognitiveLoadConstraint(3);

        this.softConstraints.add(pauseAfterReachedCognitiveLoadConstraint);
    }


    private int calculateScore(ArrayList<ScheduleEntry> schedule) throws InvalidScheduleException {

        // First update the constraints
        updateConstraints(schedule);

        // Then validate schedule (throws exception)
        isValidSchedule(schedule);

        // Lastly calculate score
        int score = 0;
        for (SoftConstraint constraint : softConstraints) {
            if (constraint.isActive) {
                score += constraint.getValue();
            }
        }

        return score; // Return a default score if all constraints are valid
    }


    /**
     * Checks whether the schedule is valid
     * @param schedule The schedule to be validated
     * @throws InvalidScheduleException if a hard constraint is violated
     */
    private void isValidSchedule(ArrayList<ScheduleEntry> schedule) throws InvalidScheduleException {

        for (HardConstraint constraint : hardConstraints) {
            if (constraint.isViolated) {
                throw new InvalidScheduleException(
                    "Hard Constraint violated: " + constraint.getClass().getSimpleName()
                );
            }
        }
    }

    /**
     * Updates the constraints for a specific schedule
     * @param schedule The schedule for which the constraints are updated
     */
    private void updateConstraints(ArrayList<ScheduleEntry> schedule) {

        // Update soft constraints
        for (SoftConstraint constraint : softConstraints) {
            constraint.updateConstraint(schedule);
        }

        // Update hard constraints
        for (HardConstraint constraint : hardConstraints) {
            constraint.updateConstraint(schedule);
        }
    }
}
