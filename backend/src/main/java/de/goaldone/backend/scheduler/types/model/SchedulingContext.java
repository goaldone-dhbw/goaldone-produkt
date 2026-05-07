package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.TaskResponse;

import java.time.LocalDate;
import java.util.List;

public record SchedulingContext(
    LocalDate fromDate,
    List<TimeSlot> availableSlots,
    List<TaskResponse> tasks
) {
}
