package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.MoveEvent;
import de.goaldone.backend.scheduler.types.model.MoveHistory;
import de.goaldone.backend.scheduler.types.moves.MoveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TabuAlgorithmusTest {

    @Mock
    private MoveHistory moveHistory;


    @Mock
    private MoveEvent moveEvent;

    @Test
    void validateMove_DetectTabuMove() {
        TabuAlgorithm algorithm = new TabuAlgorithm();

        when(moveHistory.contains(moveEvent)).thenReturn(true);

        // Same score → aspiration fails → tabu move rejected
        boolean result = algorithm.validateMove(10, 10, moveHistory, moveEvent);

        assertFalse(result);
    }

    @Test
    void validateMove_AcceptNonTabuMove() {
        TabuAlgorithm algorithm = new TabuAlgorithm();

        when(moveHistory.contains(moveEvent)).thenReturn(false);

        boolean result = algorithm.validateMove(10, 5, moveHistory, moveEvent);

        assertTrue(result);
    }


    @Test
    void moveHistory_NotContainOldMove_WhenMoreThanFiveMovesAdded() {
        TabuAlgorithm algorithm = new TabuAlgorithm();

        MoveHistory moveHistory = new MoveHistory();

        // 6 verschiedene Moves erzeugen
        MoveEvent firstMove = new MoveEvent(
                MoveType.CHANGE,
                List.of(UUID.randomUUID())
        );

        MoveEvent move2 = new MoveEvent(MoveType.SWAP, List.of(UUID.randomUUID()));
        MoveEvent move3 = new MoveEvent(MoveType.CHANGE, List.of(UUID.randomUUID()));
        MoveEvent move4 = new MoveEvent(MoveType.PILLAR, List.of(UUID.randomUUID()));
        MoveEvent move5 = new MoveEvent(MoveType.CHANGE, List.of(UUID.randomUUID()));
        MoveEvent move6 = new MoveEvent(MoveType.SWAP, List.of(UUID.randomUUID()));

        // Add 6 moves -> remove the first
        moveHistory.addMoveEvent(firstMove);
        moveHistory.addMoveEvent(move2);
        moveHistory.addMoveEvent(move3);
        moveHistory.addMoveEvent(move4);
        moveHistory.addMoveEvent(move5);
        moveHistory.addMoveEvent(move6);

        assertFalse(moveHistory.contains(firstMove));

        boolean result = algorithm.validateMove(10, 10, moveHistory, firstMove);

        assertTrue(result);
    }

    @Test
    void validateMove_AcceptTabuMove_WhenAspirationCriteriaMet() {
        TabuAlgorithm algorithm = new TabuAlgorithm();

        when(moveHistory.contains(moveEvent)).thenReturn(true);

        boolean result = algorithm.validateMove(10, 20, moveHistory, moveEvent);

        assertTrue(result);
    }

    @Test
    void validateMove_RejectTabuMove_WhenAspirationFails() {
        TabuAlgorithm algorithm = new TabuAlgorithm();

        when(moveHistory.contains(moveEvent)).thenReturn(true);

        boolean result = algorithm.validateMove(20, 10, moveHistory, moveEvent);

        assertFalse(result);
    }
}