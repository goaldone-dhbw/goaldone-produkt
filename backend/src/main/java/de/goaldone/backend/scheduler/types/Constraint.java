package de.goaldone.backend.scheduler.types;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.Schedule;
import de.goaldone.backend.scheduler.types.model.SolverState;

public interface Constraint {

    //TODO: implement constraint and set isViolated / isActive to true if violated / satisfied
    void updateConstraint(SolverState schedule);

    ScheduleWarning getWarning();
}