package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.Schedule;

import java.util.List;
import java.util.UUID;

public abstract class Move {
    public abstract Schedule apply(Schedule current);

    public abstract List<UUID> affectedChunkIds();
}
