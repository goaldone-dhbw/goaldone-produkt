package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.MoveType;
import de.goaldone.backend.scheduler.types.model.SolverState;

import java.util.Random;

public class MoveSelector {

    private final double changeMoveWeight;
    private final double swapMoveWeight;
    private final ChangeMove changeMove;
    private final SwapMove swapMove;
    private final PillarMove pillarMove;
    private final Random random;

    public MoveSelector(Random random, int maxPillarShift) {
        this(random, 0.5, 0.3, maxPillarShift);
    }

    public MoveSelector(Random random, double changeMoveWeight, double swapMoveWeight, int maxPillarShift) {
        if (changeMoveWeight + swapMoveWeight >= 1.0) {
            throw new IllegalArgumentException(
                    "changeMoveWeight + swapMoveWeight muss < 1.0 sein, damit PillarMove eine Chance hat.");
        }
        if (changeMoveWeight < 0 || swapMoveWeight < 0) {
            throw new IllegalArgumentException("Move-Gewichte dürfen nicht negativ sein.");
        }

        this.random = random;
        this.changeMoveWeight = changeMoveWeight;
        this.swapMoveWeight = swapMoveWeight;
        this.changeMove = new ChangeMove(random);
        this.swapMove = new SwapMove(random);
        this.pillarMove = new PillarMove(random, maxPillarShift);
    }

    public SolverState selectAndApply(SolverState current) {
        return selectMove().apply(current);
    }

    public Move selectMove() {
        double roll = random.nextDouble();
        if (roll < changeMoveWeight) {
            return changeMove;
        } else if (roll < changeMoveWeight + swapMoveWeight) {
            return swapMove;
        } else {
            return pillarMove;
        }
    }

    public MoveType selectedMoveType(double roll) {
        if (roll < changeMoveWeight) return MoveType.CHANGE;
        if (roll < changeMoveWeight + swapMoveWeight) return MoveType.SWAP;
        return MoveType.PILLAR;
    }
}