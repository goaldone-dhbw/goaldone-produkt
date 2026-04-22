package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.scheduler.types.Score;

import java.util.List;

public record PlanningResult(
    List<ScheduledChunk> scheduledChunks,
    Score score,
    List<UnscheduledTaskResult> unscheduledTasks
) {
}
