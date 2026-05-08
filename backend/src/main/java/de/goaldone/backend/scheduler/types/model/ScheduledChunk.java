package de.goaldone.backend.scheduler.types.model;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduledChunk(
        TaskChunk chunk,
        TimeSlot slot
) {
    public LocalDate date()      { return slot.date(); }
    public LocalTime startTime() { return slot.startTime(); }
    public LocalTime endTime()   { return slot.endTime(); }
}