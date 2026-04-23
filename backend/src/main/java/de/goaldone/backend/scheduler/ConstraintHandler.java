package de.goaldone.backend.scheduler;

import de.goaldone.backend.exception.InvalidScheduleException;
import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.constraints.DeadlineConstraint;
import de.goaldone.backend.scheduler.types.constraints.PauseAfterReachedCognitiveLoadConstraint;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SchedulingResult;

import java.util.ArrayList;
import java.util.List;

public class ConstraintHandler {

    ArrayList<SoftConstraint> softConstraints;
    ArrayList<HardConstraint> hardConstraints;

    public final int invalidScheduleScore = -10000;

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

    /**
     *
     * @param schedule The schedule for which the score is to be calculated
     * @return The score of the schedule
     * @throws   InvalidScheduleException if schedule violates hard constraints
     */
    public int calculateScore(Schedule schedule) {

        // First update the constraints for the given schedule
        updateConstraints(schedule);

        // Then validate schedule
        if (!isValidSchedule(schedule)) {
            return invalidScheduleScore; // Return a default score for invalid schedules
        };

        int score = 0;
        // Lastly calculate score
        for (SoftConstraint constraint : softConstraints) {
            if (constraint.isActive) {
                score += constraint.getValue();
            }
        }
        return score; // Return a default score if all constraints are valid
    }

    /**
     *
     * @param schedule The schedule to validate
     * @return False if the schedule violates at least one hard constraint, true otherwise
     */
    private boolean isValidSchedule(Schedule schedule) {

        // Update the constraints for the given schedule
        updateConstraints(schedule);

        // Check for violated hard constraints
        for (HardConstraint constraint : hardConstraints) {
            if (constraint.isViolated) {
                return false;
            }
        }
        return true;
    }

    /**
     *
     * @param schedule The schedule to check
     * @return A list of warnings for inactive soft constraints and violated hard constraints
     */
    public List<ScheduleWarning> getWarnings(Schedule schedule) {

        // Update the constraints for the given schedule
        updateConstraints(schedule);

        ArrayList<ScheduleWarning> scheduleWarnings = new ArrayList<>();

        // Check soft constraints
        for (SoftConstraint constraint : softConstraints) {
            if (!constraint.isActive) {
                scheduleWarnings.add(constraint.getWarning());
            }
        }

        // Check hard constraints
        for (HardConstraint constraint : hardConstraints) {
            if (constraint.isViolated) {
                scheduleWarnings.add(constraint.getWarning());
            }
        }
        return scheduleWarnings;
    }

    /**
     * Updates the constraints for a specific schedule
     * @param scheduledTasks The schedule for which the constraints are updated
     */
    private void updateConstraints(Schedule scheduledTasks) {

        // Update soft constraints
        for (SoftConstraint constraint : softConstraints) {
            constraint.updateConstraint(scheduledTasks);
        }

        // Update hard constraints
        for (HardConstraint constraint : hardConstraints) {
            constraint.updateConstraint(scheduledTasks);
        }
    }
}
