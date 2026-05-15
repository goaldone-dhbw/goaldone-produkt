package de.goaldone.backend.scheduler.types.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public record TimeSlot(
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {

    private static final int BUFFER = 15;

    public int getSlotDuration() {
        return (int) ChronoUnit.MINUTES.between(startTime, endTime);
    }

    /**
     * Checks whether the slot duration is large enough to fit the requested minutes.
     * A lower buffer of 15 minutes is applied.
     * As a result, tasks with up to 15 minutes more in duration can be shortened to fit the slot.
     * @param requestedDurationMinutes The duration needed
     * @return True, if the requested minutes fit in the time slot by also applying the lower buffer. False otherwise
     */
    public boolean canFit(int requestedDurationMinutes) {
        return (getSlotDuration() + BUFFER) >= requestedDurationMinutes;
    }
}
