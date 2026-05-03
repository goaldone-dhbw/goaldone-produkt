package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.ScheduleWarning;

import java.util.List;

public record SchedulingResult (
        SolverState schedule,
        int score,
        List<ScheduleWarning> scheduleWarnings
) {
}
