package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import de.goaldone.backend.scheduler.types.moves.ChangeMove;
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

class ChangeMoveTest {

    @Test
    void applyMovesUnpinnedChunkToFreeSlot() {
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot newSlot = slot(10, 11);

        TaskChunk chunk = chunk(UUID.randomUUID(), 0, 60, false);
        ScheduledChunk scheduledChunk = new ScheduledChunk(chunk, oldSlot);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(scheduledChunk)),
                new ArrayList<>(List.of(newSlot))
        );

        ChangeMove move = new ChangeMove(new FixedRandom(List.of(0, 0)));

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(1, next.scheduledChunks().size());

        ScheduledChunk movedChunk = next.scheduledChunks().get(0);
        assertEquals(chunk.chunkId(), movedChunk.chunk().chunkId());
        assertEquals(newSlot, movedChunk.slot());

        assertTrue(next.freeSlots().contains(oldSlot));
        assertFalse(next.freeSlots().contains(newSlot));
    }

    @Test
    void applyReturnsNullWhenAllChunksArePinned() {
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot freeSlot = slot(10, 11);

        TaskChunk pinnedChunk = chunk(UUID.randomUUID(), 0, 60, true);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(pinnedChunk, oldSlot))),
                new ArrayList<>(List.of(freeSlot))
        );

        ChangeMove move = new ChangeMove(new FixedRandom(List.of()));

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyReturnsNullWhenNoFreeSlotsExist() {
        TimeSlot oldSlot = slot(9, 10);

        TaskChunk chunk = chunk(UUID.randomUUID(), 0, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, oldSlot))),
                new ArrayList<>()
        );

        ChangeMove move = new ChangeMove(new FixedRandom(List.of()));

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyReturnsNullWhenTargetSlotIsTooShort() {
        TimeSlot oldSlot = slot(9, 11);
        TimeSlot tooShortSlot = slot(11, 11, 30);

        TaskChunk chunk = chunk(UUID.randomUUID(), 0, 90, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(chunk, oldSlot))),
                new ArrayList<>(List.of(tooShortSlot))
        );

        ChangeMove move = new ChangeMove(new FixedRandom(List.of(0, 0)));

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void applyDoesNotModifyOriginalSolverStateDirectly() {
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot newSlot = slot(10, 11);

        TaskChunk chunk = chunk(UUID.randomUUID(), 0, 60, false);
        ScheduledChunk scheduledChunk = new ScheduledChunk(chunk, oldSlot);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(scheduledChunk)),
                new ArrayList<>(List.of(newSlot))
        );

        ChangeMove move = new ChangeMove(new FixedRandom(List.of(0, 0)));

        SolverState next = move.apply(current);

        assertNotNull(next);

        assertEquals(1, current.scheduledChunks().size());
        assertEquals(oldSlot, current.scheduledChunks().get(0).slot());
        assertEquals(1, current.freeSlots().size());
        assertTrue(current.freeSlots().contains(newSlot));
    }

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(
                LocalDate.of(2026, 4, 27),
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0)
        );
    }

    private static TimeSlot slot(int startHour, int startMinute, int endMinute) {
        return new TimeSlot(
                LocalDate.of(2026, 4, 27),
                LocalTime.of(startHour, startMinute),
                LocalTime.of(startHour, endMinute)
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