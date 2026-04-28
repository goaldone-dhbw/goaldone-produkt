package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.SolverState;
import java.util.List;
import java.util.UUID;

/**
 * Abstrakte Basisklasse für Moves, die einen SolverState verändern können.
 * Ein Move beschreibt eine mögliche Veränderung des aktuellen Plans,
 * beispielsweise das Verschieben oder Tauschen von geplanten Chunks.</p>
 */
public abstract class Move {
    /**
     * Wendet den Move auf den aktuellen SolverState an.
     * @param current aktueller Zustand des Solvers
     * @return neuer SolverState nach Anwendung des Moves oder {@code null}, wenn der Move nicht gültig ist
     */
    public abstract SolverState apply(SolverState current);
    /**
     * Gibt die IDs der Chunks zurück, die durch den Move betroffen sind.
     * @return Liste der betroffenen Chunk-IDs
     */
    public abstract List<UUID> affectedChunkIds();
}