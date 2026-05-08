package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.SoftConstraint;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SolverState;

public class PauseAfterReachedCognitiveLoadConstraint extends SoftConstraint {

    public PauseAfterReachedCognitiveLoadConstraint(int value) {
        super(value);
    }

    @Override
    public void updateConstraint(SolverState schedule) {
        // TODO: implement and set isActive to true if constraint is satisfied
    }

    @Override
    public ScheduleWarning getWarning() {
        return new ScheduleWarning(
                ScheduleWarning.TypeEnum.OVERLOADED_DAY,
                "A pause should be scheduled after a certain cognitive load is reached."
        );
    }
}
