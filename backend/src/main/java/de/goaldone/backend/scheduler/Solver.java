package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.PlanningContext;
import de.goaldone.backend.scheduler.types.model.PlanningResult;

public interface Solver {
    PlanningResult createSchedule(PlanningContext context);
}
