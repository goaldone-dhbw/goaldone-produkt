package de.goaldone.backend.scheduler.types;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class Constraint {

    protected int multiplier;

    protected int value;

    public int getValue() {
        return value * multiplier;
    }

}
