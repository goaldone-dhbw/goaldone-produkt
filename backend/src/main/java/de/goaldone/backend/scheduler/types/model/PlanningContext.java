package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanningContext(
    UUID accountId,
    LocalDate fromDate,
    List<TimeSlot> availableSlots,
    List<TaskChunk> chunks,
    List<ScheduledChunk> pinnedChunks
) {
}
