package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public record TimeSlot(
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {
    public int durationMinutes() {
        return (int) ChronoUnit.MINUTES.between(startTime, endTime);
    }
}
