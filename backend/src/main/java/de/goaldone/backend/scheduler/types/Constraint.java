package de.goaldone.backend.scheduler.types;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.SolverState;

public interface Constraint {

    void updateConstraint(SolverState schedule);

    ScheduleWarning getWarning();
}