package de.goaldone.backend.scheduler.types.moves;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ChangeMove extends Move {

    private final Random random;

    public ChangeMove(Random random) {
        this.random = random;
    }

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

        next.freeSlots().add(target.slot());                          // alter Slot wird frei
        next.scheduledChunks().removeIf(sc ->
                sc.chunk().chunkId().equals(target.chunk().chunkId()));
        next.scheduledChunks().add(new ScheduledChunk(target.chunk(), newSlot));
        next.freeSlots().remove(newSlot);

        return next;
    }

    @Override
    public List<UUID> affectedChunkIds() {
        return List.of();
    }

    public List<UUID> affectedChunkIds(ScheduledChunk moved) {
        return List.of(moved.chunk().chunkId());
    }
}