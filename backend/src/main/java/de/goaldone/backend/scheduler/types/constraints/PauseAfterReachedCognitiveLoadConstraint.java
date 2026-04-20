package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.scheduler.types.SoftConstraint;

import java.util.ArrayList;

public class PauseAfterReachedCognitiveLoadConstraint extends SoftConstraint {

    public PauseAfterReachedCognitiveLoadConstraint(int value) {
        super(value);
    }

    @Override
    public void updateConstraint(ArrayList<ScheduleEntry> schedule) {
        // TODO: implement and set isActive to true if constraint is satisfied
    }
}
