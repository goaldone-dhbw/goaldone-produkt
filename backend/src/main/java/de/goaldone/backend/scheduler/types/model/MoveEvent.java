package de.goaldone.backend.scheduler.types.model;

import de.goaldone.backend.scheduler.types.moves.MoveType;

import java.util.List;
import java.util.UUID;

public record MoveEvent(
        MoveType moveType,
        List<UUID> affectedChunks
)
{
}
