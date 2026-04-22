package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.PlanningResult;

import java.util.List;
import java.util.UUID;

public abstract class Move {
    public abstract PlanningResult apply(PlanningResult current);

    public abstract List<UUID> affectedChunkIds();
}
