package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.Score;

public interface LateAcceptance {
    boolean acceptMove(Score candidateScore, Score lateScore);
}
