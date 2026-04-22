package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.moves.Move;

import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class TabuAlgorithm {

    //TODO: @Leon Parameters, Return Types
    boolean validateMove(Move move, int globalBestScore, Deque<List<UUID>> tabuList) {
        return false;
    }

    boolean isAspiration(Move move, int globalBestScore) {
        return false;
    }
}


