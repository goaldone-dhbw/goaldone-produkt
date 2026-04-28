package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Move, der einen zufällig ausgewählten nicht fixierten Chunk auf einen freien Slot verschiebt.
 * Der Move ist nur gültig, wenn ein nicht fixierter Chunk und ein freier Slot vorhanden sind
 * und der Zielslot lang genug für den Chunk ist.</p>
 */
public class ChangeMove extends Move {

    private final Random random;

    /**
     * Erstellt einen neuen ChangeMove.
     * @param random Zufallsgenerator für die Auswahl von Chunk und Zielslot
     */
    public ChangeMove(Random random) {
        this.random = random;
    }

    /**
     * Verschiebt einen zufällig ausgewählten nicht fixierten Chunk auf einen zufällig ausgewählten freien Slot.
     * @param current aktueller Zustand des Solvers
     * @return neuer SolverState nach der Verschiebung oder {@code null}, wenn kein gültiger Move möglich ist
     */
    @Override
    public SolverState apply(SolverState current) {
        List<ScheduledChunk> unpinned = current.scheduledChunks().stream()
                .filter(sc -> !sc.chunk().isPinned())
                .toList();

        if (unpinned.isEmpty() || current.freeSlots().isEmpty()) {
            return null;
        }

        ScheduledChunk target = unpinned.get(random.nextInt(unpinned.size()));
        TimeSlot newSlot = current.freeSlots().get(random.nextInt(current.freeSlots().size()));

        if (newSlot.durationMinutes() < target.chunk().durationMinutes()) {
            return null;
        }
        SolverState next = current.deepCopy();

        next.freeSlots().add(target.slot());
        next.scheduledChunks().removeIf(sc ->
                sc.chunk().chunkId().equals(target.chunk().chunkId()));
        next.scheduledChunks().add(new ScheduledChunk(target.chunk(), newSlot));
        next.freeSlots().remove(newSlot);

        return next;
    }

    /**
     * Gibt die betroffenen Chunk-IDs für diesen Move zurück.
     * @return leere Liste, da die betroffenen Chunks erst bei konkreter Auswahl bekannt sind
     */
    @Override
    public List<UUID> affectedChunkIds() {
        return List.of();
    }

    /**
     * Gibt die ID des verschobenen Chunks zurück.
     * @param moved verschobener Chunk
     * @return Liste mit der ID des verschobenen Chunks
     */
    public List<UUID> affectedChunkIds(ScheduledChunk moved) {
        return List.of(moved.chunk().chunkId());
    }
}