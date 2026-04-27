package de.goaldone.backend.scheduler.types.moves;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SwapMove extends Move {
    private final Random random;
    public SwapMove(Random random) {
        this.random = random;
    }

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

        if (slotB.durationMinutes() < a.chunk().durationMinutes()) return null;
        if (slotA.durationMinutes() < b.chunk().durationMinutes()) return null;

        SolverState next = current.deepCopy();

        next.scheduledChunks().removeIf(sc ->
                sc.chunk().chunkId().equals(a.chunk().chunkId()) ||
                        sc.chunk().chunkId().equals(b.chunk().chunkId())
        );

        next.scheduledChunks().add(new ScheduledChunk(a.chunk(), slotB));
        next.scheduledChunks().add(new ScheduledChunk(b.chunk(), slotA));

        return next;
    }

    @Override
    public List<UUID> affectedChunkIds() {
        return List.of();
    }

    public List<UUID> affectedChunkIds(ScheduledChunk a, ScheduledChunk b) {
        return List.of(a.chunk().chunkId(), b.chunk().chunkId());
    }
}