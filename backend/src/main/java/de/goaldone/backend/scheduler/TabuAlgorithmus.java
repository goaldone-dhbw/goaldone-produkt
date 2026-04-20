package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.Score;
import de.goaldone.backend.scheduler.types.moves.Move;

import java.util.Deque;
import java.util.List;
import java.util.UUID;

public interface TabuAlgorithmus {
    boolean validateMove(Move move, Score globalBestScore, Deque<List<UUID>> tabuList);

    boolean isAspiration(Move move, Score globalBestScore);
}

