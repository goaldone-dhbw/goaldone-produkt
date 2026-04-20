package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.PlanningResult;

import java.util.List;
import java.util.UUID;

public class ChangeMove extends Move {
    @Override
    public PlanningResult apply(PlanningResult current) {
        // TODO: Move one chunk to a different time slot (~50% probability in move selection)
        throw new UnsupportedOperationException("ChangeMove implementation pending");
    }

    @Override
    public List<UUID> affectedChunkIds() {
        // TODO: Return list of affected chunk IDs for tabu tracking
        throw new UnsupportedOperationException("ChangeMove implementation pending");
    }
}
