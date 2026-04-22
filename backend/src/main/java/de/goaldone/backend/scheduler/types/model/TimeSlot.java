package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record TimeSlot(
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {
    public int durationMinutes() {
        return (int) java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
    }
}
