package de.goaldone.backend.scheduler.types;

import de.goaldone.backend.model.ScheduleWarning;
import de.goaldone.backend.scheduler.types.model.Schedule;

public interface Constraint {

    //TODO: implement constraint and set isViolated / isActive to true if violated / satisfied
    void updateConstraint(Schedule schedule);

    ScheduleWarning getWarning();
}