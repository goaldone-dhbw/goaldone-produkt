package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.Schedule;

import java.util.List;
import java.util.UUID;

public class PillarMove extends Move {
    @Override
    public Schedule apply(Schedule current) {
        // TODO: Move all chunks of one task together (~20% probability in move selection)
        throw new UnsupportedOperationException("PillarMove implementation pending");
    }

    @Override
    public List<UUID> affectedChunkIds() {
        // TODO: Return list of all affected chunk IDs for the task
        throw new UnsupportedOperationException("PillarMove implementation pending");
    }
}
