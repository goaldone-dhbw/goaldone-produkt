package de.goaldone.backend.scheduler.types;

public abstract class SoftConstraint implements Constraint {

    private final int constraintValue;
    public boolean isActive;

    /**
     * @param value The value of the soft constraint.
     *              This value is added to the total score if the constraint is satisfied.
     *              It can either be negative, if violating the constraint has negative impact on the overall schedule
     *              or positive, if satisfying the constraint has a positive impact on the overall schedule.
     *
     */
    public SoftConstraint(int value) {
        this.constraintValue = value;
        this.isActive = false;
    }

    public int getValue() {
        return this.constraintValue;
    }
}
