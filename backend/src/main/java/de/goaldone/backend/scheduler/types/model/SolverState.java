package de.goaldone.backend.scheduler.types.model;

import java.util.ArrayList;
import java.util.List;

public record SolverState(
        List<ScheduledChunk> scheduledChunks,
        List<TimeSlot> freeSlots
) {
    public SolverState deepCopy() {
        return new SolverState(
                new ArrayList<>(scheduledChunks),
                new ArrayList<>(freeSlots)
        );
    }
}