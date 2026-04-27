package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.util.List;

public record SchedulingContext(
    LocalDate fromDate,
    List<TimeSlot> availableSlots,
    List<TaskChunk> chunks
) {
}
