package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.MoveEvent;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Move, der die Slots zweier zufällig ausgewählter nicht fixierter Chunks tauscht.
 * Der Move ist nur gültig, wenn mindestens zwei nicht fixierte Chunks vorhanden sind
 * und beide Chunks jeweils in den Slot des anderen Chunks passen.
 */
public class SwapMove extends Move {
    private final Random random;

    /**
     * Erstellt einen neuen SwapMove.
     * @param random Zufallsgenerator für die Auswahl der zu tauschenden Chunks
     */
    public SwapMove(Random random) {
        this.random = random;
    }

    /**
     * Tauscht die Slots zweier zufällig ausgewählter nicht fixierter Chunks.
     * @param current aktueller Zustand des Solvers
     * @return neuer SolverState nach dem Slot-Tausch oder {@code null}, wenn kein gültiger Tausch möglich ist
     */
    @Override
    public SolverState apply(SolverState current) {
        List<ScheduledChunk> unpinned = current.scheduledChunks().stream()
                .filter(sc -> !sc.chunk().isPinned())
                .toList();

        if (unpinned.size() < 2) {
            return null;
        }

        int idxA = random.nextInt(unpinned.size());
        int idxB;
        do {
            idxB = random.nextInt(unpinned.size());
        } while (idxB == idxA);

        ScheduledChunk a = unpinned.get(idxA);
        ScheduledChunk b = unpinned.get(idxB);

        TimeSlot slotA = a.slot();
        TimeSlot slotB = b.slot();

        // Update move event
        List<UUID> affectedChunks = List.of(a.chunk().chunkId(), b.chunk().chunkId());
        moveEvent = new MoveEvent(MoveType.SWAP, affectedChunks);

        if (!isValidSwap(a, b)) {
            return null;
        }

        SolverState next = current.deepCopy();

        next.scheduledChunks().removeIf(sc ->
                sc.chunk().chunkId().equals(a.chunk().chunkId()) ||
                        sc.chunk().chunkId().equals(b.chunk().chunkId())
        );

        next.scheduledChunks().add(new ScheduledChunk(a.chunk(), slotB));
        next.scheduledChunks().add(new ScheduledChunk(b.chunk(), slotA));
        return next;
    }

    private boolean isValidSwap(ScheduledChunk a, ScheduledChunk b) {
        return b.slot().durationMinutes() >= a.chunk().durationMinutes()
                && a.slot().durationMinutes() >= b.chunk().durationMinutes();
    }
}