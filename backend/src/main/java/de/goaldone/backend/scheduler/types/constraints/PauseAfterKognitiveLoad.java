package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.scheduler.types.SoftConstraint;

public class PauseAfterKognitiveLoad extends SoftConstraint {

    public PauseAfterKognitiveLoad(int value) {
        super(2);
    }
}
