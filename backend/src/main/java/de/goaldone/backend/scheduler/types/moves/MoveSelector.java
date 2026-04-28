package de.goaldone.backend.scheduler.types.moves;

import de.goaldone.backend.scheduler.types.model.SolverState;
import java.util.Random;

/**
 * Wählt anhand gewichteter Wahrscheinlichkeiten einen Move aus und kann ihn direkt anwenden.
 * Standardmäßig werden ChangeMove, SwapMove und PillarMove mit festen Gewichten ausgewählt.
 * Die verbleibende Wahrscheinlichkeit nach ChangeMove und SwapMove entfällt auf den PillarMove.</p>
 */
public class MoveSelector {

    private final double changeMoveWeight;
    private final double swapMoveWeight;
    private final ChangeMove changeMove;
    private final SwapMove swapMove;
    private final PillarMove pillarMove;
    private final Random random;

    /**
     * Erstellt einen MoveSelector mit Standardgewichten.
     * @param random Zufallsgenerator für die Move-Auswahl
     * @param maxPillarShift maximale Slot-Verschiebung für den PillarMove
     */
    public MoveSelector(Random random, int maxPillarShift) {
        this(random, 0.5, 0.3, maxPillarShift);
    }

    /**
     * Erstellt einen MoveSelector mit frei konfigurierbaren Gewichten.
     * @param random Zufallsgenerator für die Move-Auswahl
     * @param changeMoveWeight Wahrscheinlichkeit für die Auswahl eines ChangeMove
     * @param swapMoveWeight Wahrscheinlichkeit für die Auswahl eines SwapMove
     * @param maxPillarShift maximale Slot-Verschiebung für den PillarMove
     * @throws IllegalArgumentException wenn die Gewichte ungültig sind
     */
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

    /**
     * Wählt einen Move aus und wendet ihn direkt auf den aktuellen SolverState an.
     * @param current aktueller Zustand des Solvers
     * @return neuer SolverState nach Anwendung des ausgewählten Moves oder {@code null}, wenn der Move ungültig ist
     */
    public SolverState selectAndApply(SolverState current) {
        return selectMove().apply(current);
    }

    /**
     * Wählt anhand der konfigurierten Gewichte einen Move aus.
     * @return ausgewählter Move
     */
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
}