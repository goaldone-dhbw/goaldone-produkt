package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Move, der alle nicht fixierten Chunks einer zufällig ausgewählten Aufgabe gemeinsam verschiebt.
 * Die Chunks werden um eine zufällige Anzahl von Slots nach vorne oder hinten verschoben.
 * Dabei bleiben Reihenfolge und Abstand der Chunks innerhalb der Aufgabe erhalten.</p>
 * Der Move ist nur gültig, wenn alle Zielslots verfügbar sind oder bereits zur selben Aufgabe
 * gehören und alle zeitlichen Einschränkungen der Chunks eingehalten werden.</p>
 */
public class PillarMove extends Move {
    private final Random random;
    private final int maxShift;

    /**
     * Erstellt einen neuen PillarMove.
     * @param random Zufallsgenerator für die Auswahl der Aufgabe und der Verschiebung
     * @param maxShift maximale Anzahl an Slots, um die eine Aufgabe verschoben werden darf
     * @throws IllegalArgumentException wenn maxShift kleiner oder gleich 0 ist
     */
    public PillarMove(Random random, int maxShift) {
        if (maxShift <= 0) {
            throw new IllegalArgumentException("maxShift muss größer als 0 sein.");
        }
        this.random = random;
        this.maxShift = maxShift;
    }

    /**
     * Verschiebt alle nicht fixierten Chunks einer zufällig ausgewählten Aufgabe gemeinsam.
     * @param current aktueller Zustand des Solvers
     * @return neuer SolverState nach der Verschiebung oder {@code null}, wenn kein gültiger Move möglich ist
     */
    @Override
    public SolverState apply(SolverState current) {
        Map<UUID, List<ScheduledChunk>> chunksByTask = current.scheduledChunks().stream()
                .filter(sc -> !sc.chunk().isPinned())
                .collect(Collectors.groupingBy(sc -> sc.chunk().taskId()));

        if (chunksByTask.isEmpty()) {
            return null;
        }

        List<UUID> taskIds = new ArrayList<>(chunksByTask.keySet());
        UUID targetTaskId = taskIds.get(random.nextInt(taskIds.size()));
        List<ScheduledChunk> taskChunks = chunksByTask.get(targetTaskId).stream()
                .sorted(Comparator.comparingInt(sc -> sc.chunk().chunkIndex()))
                .toList();


        // Update affected chunks
        affectedChunks = taskChunks.stream()
                .map(sc -> sc.chunk().chunkId())
                .toList();


        int shift = 0;
        while (shift == 0) {
            shift = random.nextInt(2 * maxShift + 1) - maxShift;
        }

        List<TimeSlot> allSlots = buildAllSlots(current);

        if (allSlots.isEmpty()) {
            return null;
        }

        List<TimeSlot> newSlotsForChunks = new ArrayList<>();
        List<TimeSlot> freedSlots = new ArrayList<>();

        for (ScheduledChunk chunk : taskChunks) {
            int currentIndex = allSlots.indexOf(chunk.slot());
            if (currentIndex < 0) {
                return null;
            }

            int targetIndex = currentIndex + shift;
            if (targetIndex < 0 || targetIndex >= allSlots.size()) {
                return null;
            }

            TimeSlot targetSlot = allSlots.get(targetIndex);
            boolean slotIsFree = current.freeSlots().contains(targetSlot);
            boolean slotBelongsToSameTask = current.scheduledChunks().stream()
                    .anyMatch(sc -> sc.chunk().taskId().equals(targetTaskId)
                            && sc.slot().equals(targetSlot));

            if (!slotIsFree && !slotBelongsToSameTask) {
                return null;
            }

            if (!isValidTargetSlot(targetSlot, chunk)) {
                return null;
            }

            newSlotsForChunks.add(targetSlot);
            freedSlots.add(chunk.slot());
        }

        SolverState next = current.deepCopy();
        next.scheduledChunks().removeIf(sc ->
                sc.chunk().taskId().equals(targetTaskId) && !sc.chunk().isPinned()
        );

        for (int i = 0; i < taskChunks.size(); i++) {
            next.scheduledChunks().add(new ScheduledChunk(
                    taskChunks.get(i).chunk(),
                    newSlotsForChunks.get(i)
            ));
            next.freeSlots().remove(newSlotsForChunks.get(i));
        }

        for (TimeSlot freed : freedSlots) {
            if (!newSlotsForChunks.contains(freed)) {
                next.freeSlots().add(freed);
            }
        }

        return next;
    }

    @Override
    public MoveType getMoveType() {
        return MoveType.PILLAR;
    }


    private List<TimeSlot> buildAllSlots(SolverState state) {
        List<TimeSlot> all = new ArrayList<>(state.freeSlots());
        state.scheduledChunks().forEach(sc -> all.add(sc.slot()));
        all.sort(Comparator.comparing(TimeSlot::date).thenComparing(TimeSlot::startTime));
        return all;
    }

    private boolean isValidTargetSlot(TimeSlot targetSlot, ScheduledChunk chunk) {
        if (targetSlot.durationMinutes() < chunk.chunk().durationMinutes()) {
            return false;
        }

        if (chunk.chunk().deadline() != null) {
            LocalDateTime chunkEnd = LocalDateTime.of(targetSlot.date(), targetSlot.endTime());
            if (chunkEnd.isAfter(chunk.chunk().deadline())) {
                return false;
            }
        }

        if (chunk.chunk().notBefore() != null) {
            LocalDateTime chunkStart = LocalDateTime.of(targetSlot.date(), targetSlot.startTime());
            if (chunkStart.isBefore(chunk.chunk().notBefore())) {
                return false;
            }
        }
        return true;
    }
}