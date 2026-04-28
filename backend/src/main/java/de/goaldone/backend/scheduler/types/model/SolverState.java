package de.goaldone.backend.scheduler.types.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Beschreibt den aktuellen Zustand des Solvers.
 * @param scheduledChunks aktuell eingeplante Chunks
 * @param freeSlots aktuell freie Slots
 */
public record SolverState(
        List<ScheduledChunk> scheduledChunks,
        List<TimeSlot> freeSlots
) {

    /**
     * Erstellt eine Kopie des SolverState mit neuen Listeninstanzen.
     * Die enthaltenen Objekte selbst werden nicht tief kopiert.</p>
     * @return kopierter SolverState
     */
    public SolverState deepCopy() {
        return new SolverState(
                new ArrayList<>(scheduledChunks),
                new ArrayList<>(freeSlots)
        );
    }
}