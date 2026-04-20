package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.scheduler.types.HardConstaint;

public class DeadlineConstraint extends HardConstaint {

    public DeadlineConstraint() {
        super(-10);
    }
}
