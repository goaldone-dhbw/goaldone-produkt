package de.goaldone.backend.scheduler.types;

import de.goaldone.backend.model.ScheduleEntry;

import java.util.ArrayList;

public interface Constraint {

    //TODO: implement constraint and set isViolated / isActive to true if violated / satisfied
    void updateConstraint(ArrayList<ScheduleEntry> schedule);
}