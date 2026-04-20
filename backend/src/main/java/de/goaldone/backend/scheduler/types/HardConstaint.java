package de.goaldone.backend.scheduler.types;

public abstract class HardConstaint extends Constraint {

    public HardConstaint(int value) {
        if (value > 0) {
            throw new IllegalArgumentException("Constraint value for hard constraint must be less than or equal to 0");
        }
        this.value = value;
        this.multiplier = 100;
    }
}
