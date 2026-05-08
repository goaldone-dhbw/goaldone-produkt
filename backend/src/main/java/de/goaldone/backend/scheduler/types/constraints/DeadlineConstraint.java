package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleEntry;
import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.HardConstraint;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.util.ArrayList;

public class DeadlineConstraint extends HardConstraint {

    @Override
    public void updateConstraint(SolverState schedule) {
        //TODO: implement constraint and set this.isViolated = true if violated
    }

    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
            ScheduleWarning.TypeEnum.DEADLINE_TIGHT,
            "One or more tasks are scheduled after their deadline."
        );
    }
}
