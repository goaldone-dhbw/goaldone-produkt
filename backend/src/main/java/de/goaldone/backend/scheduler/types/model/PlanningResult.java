package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.ScheduleWarning;

import java.util.ArrayList;

public record PlanningResult(
    int score,
    ArrayList<ScheduledChunk> scheduledChunks,
    ArrayList<UnscheduledTaskResult> unscheduledTasks,
    ArrayList<ScheduleWarning> scheduleWarnings
) {
}
