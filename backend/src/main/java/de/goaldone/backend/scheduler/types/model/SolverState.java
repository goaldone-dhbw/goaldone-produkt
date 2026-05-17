package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.model.UnscheduledTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Beschreibt den aktuellen Zustand des Solvers.
 * @param scheduledChunks aktuell eingeplante Chunks
 * @param freeSlots aktuell freie Slots
 * @param unscheduledTasks Liste der noch nicht eingeplanten Tasks
 * @param context der unveränderliche Scheduling-Kontext (nicht kopiert, da Record)
 */
public record SolverState(
        List<ScheduledChunk> scheduledChunks,
        List<TimeSlot> freeSlots,
        List<UnscheduledTask> unscheduledTasks,
        SchedulingContext context
) {

    /**
     * Erstellt eine Kopie des SolverState mit neuen Listeninstanzen.
     * Die enthaltenen Objekte selbst werden nicht tief kopiert.</p>
     * @return kopierter SolverState
     */
    public SolverState deepCopy() {
        return new SolverState(
                new ArrayList<>(scheduledChunks),
                new ArrayList<>(freeSlots),
                unscheduledTasks == null
                        ? null
                        : new ArrayList<>(unscheduledTasks),
                context // SchedulingContext is a record and therefore immutable – no copy needed
        );
    }
}