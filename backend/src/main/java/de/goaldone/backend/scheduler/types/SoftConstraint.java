package de.goaldone.backend.scheduler.types;

public abstract class SoftConstraint extends Constraint {

    public SoftConstraint(int value) {
        this.value = value;
        this.multiplier = 1;
    }
}
