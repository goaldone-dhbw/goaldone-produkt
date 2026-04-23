package de.goaldone.backend.scheduler.types;

import de.goaldone.backend.model.ScheduleWarning;

public abstract class HardConstraint implements Constraint{

    public boolean isViolated;

    /**
     * A hard constraint is a constraint that must not be violated in order for a schedule to be considered valid.
     * If a hard constraint is violated, the schedule is considered invalid and should not be used.
     */

    public HardConstraint() {
        this.isViolated = false;
    }

}
