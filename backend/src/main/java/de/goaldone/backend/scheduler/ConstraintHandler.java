package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.constraints.DeadlineConstraint;
import de.goaldone.backend.scheduler.types.constraints.PauseAfterReachedCognitiveLoadConstraint;
import de.goaldone.backend.scheduler.types.model.SolverState;

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
        //TODO: Add constraints

        // Soft constraints
        PauseAfterReachedCognitiveLoadConstraint pauseAfterReachedCognitiveLoadConstraint =
                new PauseAfterReachedCognitiveLoadConstraint(3);

        this.softConstraints.add(pauseAfterReachedCognitiveLoadConstraint);
        //TODO: Add constraints
    }

    /**
     *
     * @param solverState The schedule for which the score is to be calculated
     * @return The score of the schedule
     */
    public int calculateScore(SolverState solverState) {

        // First update the constraints for the given schedule
        updateConstraints(solverState);

        // Then validate schedule
        if (!isValidSchedule()) {
            return invalidScheduleScore; // Return a default score for invalid schedules
        }

        int score = 0;
        // Lastly calculate score
        for (SoftConstraint constraint : softConstraints) {
            if (constraint.isActive) {
                score += constraint.getValue();
            }
        }
        return score;
    }

    /**
     *
     * @return False if the schedule violates at least one hard constraint, true otherwise
     */
    private boolean isValidSchedule() {

        // Check for violated hard constraints
        for (HardConstraint constraint : hardConstraints) {
            if (constraint.isViolated) {
                return false;
            }
        }
        return true;
    }


    ArrayList<ScheduleWarning> addUnscheduledWarning(int numUnscheduled) {
        String message;
        if (numUnscheduled == 0) {
            return new ArrayList<>();
        }
        else if (numUnscheduled == 1) {
            message = "There is one unscheduled chunk due to lack of available time";
        }
        else {
            message = "There are " + numUnscheduled + " unscheduled chunks due to lack of available time";
        }
        return new ArrayList<>(
                List.of(new ScheduleWarning(
                    ScheduleWarning.TypeEnum.OTHER,
                    message
        )));
    }

    /**
     * @param schedule The schedule to check
     * @return A list of warnings for inactive soft constraints, violated hard constraints and unscheduled chunks.
     */
    public List<ScheduleWarning> getWarnings(SolverState schedule) {

        // Add warning for unscheduled tasks
        ArrayList<ScheduleWarning> scheduleWarnings = addUnscheduledWarning(
                schedule.unscheduledChunks().size()
        );

        // Update the constraints for the given schedule
        updateConstraints(schedule);


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
    private void updateConstraints(SolverState scheduledTasks) {

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
