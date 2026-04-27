package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import de.goaldone.backend.scheduler.types.moves.PillarMove;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PillarMoveTest {

    @Test
    void constructorThrowsExceptionWhenMaxShiftIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> new PillarMove(new FixedRandom(List.of()), 0));
        assertThrows(IllegalArgumentException.class, () -> new PillarMove(new FixedRandom(List.of()), -1));
    }

    @Test
    void applyMovesAllUnpinnedChunksOfOneTaskTogether() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot slot1 = slot(10, 11);
        TimeSlot slot2 = slot(11, 12);

        TaskChunk chunk0 = chunk(taskId, 0, 60, false, null, null);
        TaskChunk chunk1 = chunk(taskId, 1, 60, false, null, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(chunk0, slot0),
                        new ScheduledChunk(chunk1, slot1)
                )),
                new ArrayList<>(List.of(slot2))
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(slot1, findSlot(next, chunk0.chunkId()));
        assertEquals(slot2, findSlot(next, chunk1.chunkId()));

        assertTrue(next.freeSlots().contains(slot0));
        assertFalse(next.freeSlots().contains(slot2));
    }

    @Test
    void orderAndDistanceRemainAfterMoving() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot slot1 = slot(10, 11);
        TimeSlot slot2 = slot(11, 12);

        TaskChunk chunk0 = chunk(taskId, 0, 60, false, null, null);
        TaskChunk chunk1 = chunk(taskId, 1, 60, false, null, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(chunk0, slot0),
                        new ScheduledChunk(chunk1, slot1)
                )),
                new ArrayList<>(List.of(slot2))
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNotNull(next);

        TimeSlot newSlot0 = findSlot(next, chunk0.chunkId());
        TimeSlot newSlot1 = findSlot(next, chunk1.chunkId());

        assertTrue(newSlot0.startTime().isBefore(newSlot1.startTime()));
        assertEquals(60, java.time.temporal.ChronoUnit.MINUTES.between(newSlot0.startTime(), newSlot1.startTime()));
    }

    @Test
    void applyReturnsNullWhenTargetSlotIsOutsideSlotRange() {
        UUID taskId = UUID.randomUUID();

        TimeSlot lastSlot = slot(9, 10);

        TaskChunk chunk = chunk(taskId, 0, 60, false, null, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, lastSlot))),
                new ArrayList<>()
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyReturnsNullWhenTargetSlotIsOccupiedByForeignTask() {
        UUID targetTaskId = UUID.randomUUID();
        UUID foreignTaskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot occupiedSlot = slot(10, 11);

        TaskChunk targetChunk = chunk(targetTaskId, 0, 60, false, null, null);
        TaskChunk foreignPinnedChunk = chunk(foreignTaskId, 0, 60, true, null, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(targetChunk, slot0),
                        new ScheduledChunk(foreignPinnedChunk, occupiedSlot)
                )),
                new ArrayList<>()
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyReturnsNullWhenTargetSlotWouldExceedDeadline() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot slot1 = slot(10, 11);

        LocalDateTime deadline = LocalDateTime.of(slot1.date(), LocalTime.of(10, 30));

        TaskChunk chunk = chunk(taskId, 0, 60, false, null, deadline);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, slot0))),
                new ArrayList<>(List.of(slot1))
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyReturnsNullWhenTargetSlotWouldStartBeforeNotBefore() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot slot1 = slot(10, 11);

        LocalDateTime notBefore = LocalDateTime.of(slot1.date(), LocalTime.of(10, 0));

        TaskChunk chunk = chunk(taskId, 0, 60, false, notBefore, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, slot1))),
                new ArrayList<>(List.of(slot0))
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 0)), 1);

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void pinnedChunksAreNotMoved() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slot0 = slot(9, 10);
        TimeSlot slot1 = slot(10, 11);
        TimeSlot slot2 = slot(11, 12);

        TaskChunk movableChunk = chunk(taskId, 0, 60, false, null, null);
        TaskChunk pinnedChunk = chunk(taskId, 1, 60, true, null, null);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(movableChunk, slot0),
                        new ScheduledChunk(pinnedChunk, slot2)
                )),
                new ArrayList<>(List.of(slot1))
        );

        PillarMove move = new PillarMove(new FixedRandom(List.of(0, 2)), 1);

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(slot1, findSlot(next, movableChunk.chunkId()));
        assertEquals(slot2, findSlot(next, pinnedChunk.chunkId()));
    }

    private static TimeSlot findSlot(SolverState state, UUID chunkId) {
        return state.scheduledChunks().stream()
                .filter(sc -> sc.chunk().chunkId().equals(chunkId))
                .findFirst()
                .orElseThrow()
                .slot();
    }

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(
                LocalDate.of(2026, 4, 27),
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0)
        );
    }

    private static TaskChunk chunk(
            UUID taskId,
            int chunkIndex,
            int durationMinutes,
            boolean pinned,
            LocalDateTime notBefore,
            LocalDateTime deadline
    ) {
        return new TaskChunk(
                UUID.randomUUID(),
                taskId,
                chunkIndex,
                2,
                durationMinutes,
                0,
                0,
                null,
                notBefore,
                deadline,
                pinned,
                null
        );
    }

    private static class FixedRandom extends Random {
        private final Queue<Integer> ints;

        FixedRandom(List<Integer> ints) {
            this.ints = new ArrayDeque<>(ints);
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