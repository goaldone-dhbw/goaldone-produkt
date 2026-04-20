package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.scheduler.types.HardConstraint;

import java.util.ArrayList;

public class DeadlineConstraint extends HardConstraint {

    @Override
    public void updateConstraint(ArrayList<ScheduleEntry> schedule) {
        //TODO: implement constraint and set this.isViolated = true if violated
    }
}
