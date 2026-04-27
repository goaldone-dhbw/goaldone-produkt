package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.SolverState;
import java.util.List;
import java.util.UUID;

public abstract class Move {
    public abstract SolverState apply(SolverState current);
    public abstract List<UUID> affectedChunkIds();
}