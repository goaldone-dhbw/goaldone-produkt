package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduledChunk(
    TaskChunk chunk,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {
}
