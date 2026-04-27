package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.MoveType;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import de.goaldone.backend.scheduler.types.moves.MoveSelector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MoveSelectorTest {

    @Test
    void selectedMoveTypeReturnsChangeForRollBelowChangeWeight() {
        MoveSelector selector = new MoveSelector(new FixedRandom(List.of(), List.of()), 1);

        assertEquals(MoveType.CHANGE, selector.selectedMoveType(0.49));
    }

    @Test
    void selectedMoveTypeReturnsSwapForRollAtChangeBoundary() {
        MoveSelector selector = new MoveSelector(new FixedRandom(List.of(), List.of()), 1);

        assertEquals(MoveType.SWAP, selector.selectedMoveType(0.50));
    }

    @Test
    void selectedMoveTypeReturnsSwapForRollBelowPillarBoundary() {
        MoveSelector selector = new MoveSelector(new FixedRandom(List.of(), List.of()), 1);

        assertEquals(MoveType.SWAP, selector.selectedMoveType(0.79));
    }

    @Test
    void selectedMoveTypeReturnsPillarForRollAtPillarBoundary() {
        MoveSelector selector = new MoveSelector(new FixedRandom(List.of(), List.of()), 1);

        assertEquals(MoveType.PILLAR, selector.selectedMoveType(0.80));
    }

    @Test
    void constructorThrowsExceptionWhenChangeAndSwapWeightAreTooLarge() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveSelector(new FixedRandom(List.of(), List.of()), 0.5, 0.5, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveSelector(new FixedRandom(List.of(), List.of()), 0.7, 0.4, 1)
        );
    }

    @Test
    void constructorThrowsExceptionWhenWeightsAreNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveSelector(new FixedRandom(List.of(), List.of()), -0.1, 0.3, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new MoveSelector(new FixedRandom(List.of(), List.of()), 0.5, -0.1, 1)
        );
    }

    @Test
    void selectAndApplyReturnsMoveResultWhenSelectedMoveIsValid() {
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot newSlot = slot(10, 11);

        UUID taskId = UUID.randomUUID();
        TaskChunk chunk = chunk(taskId, 0, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, oldSlot))),
                new ArrayList<>(List.of(newSlot))
        );

        /*
         * nextDouble = 0.1 -> ChangeMove wird ausgewählt.
         * nextInt(1) = 0 -> erster Chunk.
         * nextInt(1) = 0 -> erster freier Slot.
         */
        MoveSelector selector = new MoveSelector(
                new FixedRandom(List.of(0.1), List.of(0, 0)),
                1
        );

        SolverState next = selector.selectAndApply(current);

        assertNotNull(next);
        assertEquals(newSlot, next.scheduledChunks().get(0).slot());
        assertTrue(next.freeSlots().contains(oldSlot));
        assertFalse(next.freeSlots().contains(newSlot));
    }

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(
                LocalDate.of(2026, 4, 27),
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0)
        );
    }

    private static TaskChunk chunk(UUID taskId, int chunkIndex, int durationMinutes, boolean pinned) {
        return new TaskChunk(
                UUID.randomUUID(),
                taskId,
                chunkIndex,
                1,
                durationMinutes,
                0,
                0,
                null,
                null,
                null,
                pinned,
                null
        );
    }

    private static class FixedRandom extends Random {
        private final Queue<Double> doubles;
        private final Queue<Integer> ints;

        FixedRandom(List<Double> doubles, List<Integer> ints) {
            this.doubles = new ArrayDeque<>(doubles);
            this.ints = new ArrayDeque<>(ints);
        }

        @Override
        public double nextDouble() {
            if (doubles.isEmpty()) {
                fail("No fixed double value left for nextDouble()");
            }

            return doubles.remove();
        }

        @Override
        public int nextInt(int bound) {
            if (ints.isEmpty()) {
                fail("No fixed int value left for nextInt(" + bound + ")");
            }

            int value = ints.remove();

            if (value < 0 || value >= bound) {
                fail("Fixed int value " + value + " is outside bound " + bound);
            }

            return value;
        }
    }
}