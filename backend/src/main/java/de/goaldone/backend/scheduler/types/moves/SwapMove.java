package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.Schedule;

import java.util.List;
import java.util.UUID;

public class SwapMove extends Move {
    @Override
    public Schedule apply(Schedule current) {
        // TODO: Swap two chunks between their time slots (~30% probability in move selection)
        throw new UnsupportedOperationException("SwapMove implementation pending");
    }

    @Override
    public List<UUID> affectedChunkIds() {
        // TODO: Return list of affected chunk IDs for tabu tracking
        throw new UnsupportedOperationException("SwapMove implementation pending");
    }
}
