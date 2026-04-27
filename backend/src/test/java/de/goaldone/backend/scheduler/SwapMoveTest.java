package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import de.goaldone.backend.scheduler.types.moves.SwapMove;
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

class SwapMoveTest {

    @Test
    void applySwapsSlotsOfTwoUnpinnedChunks() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slotA = slot(9, 10);
        TimeSlot slotB = slot(10, 11);

        TaskChunk chunkA = chunk(taskId, 0, 60, false);
        TaskChunk chunkB = chunk(taskId, 1, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(chunkA, slotA),
                        new ScheduledChunk(chunkB, slotB)
                )),
                new ArrayList<>()
        );

        SwapMove move = new SwapMove(new FixedRandom(List.of(0, 1)));

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(slotB, findSlot(next, chunkA.chunkId()));
        assertEquals(slotA, findSlot(next, chunkB.chunkId()));
    }

    @Test
    void applyReturnsNullWhenLessThanTwoUnpinnedChunksExist() {
        UUID taskId = UUID.randomUUID();

        TaskChunk onlyChunk = chunk(taskId, 0, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(new ScheduledChunk(onlyChunk, slot(9, 10)))),
                new ArrayList<>()
        );

        SwapMove move = new SwapMove(new FixedRandom(List.of()));

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void pinnedChunksAreNotSelected() {
        UUID taskId = UUID.randomUUID();

        TimeSlot pinnedSlot = slot(8, 9);
        TimeSlot slotA = slot(9, 10);
        TimeSlot slotB = slot(10, 11);

        TaskChunk pinnedChunk = chunk(taskId, 0, 60, true);
        TaskChunk chunkA = chunk(taskId, 1, 60, false);
        TaskChunk chunkB = chunk(taskId, 2, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(pinnedChunk, pinnedSlot),
                        new ScheduledChunk(chunkA, slotA),
                        new ScheduledChunk(chunkB, slotB)
                )),
                new ArrayList<>()
        );

        SwapMove move = new SwapMove(new FixedRandom(List.of(0, 1)));

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(pinnedSlot, findSlot(next, pinnedChunk.chunkId()));
        assertEquals(slotB, findSlot(next, chunkA.chunkId()));
        assertEquals(slotA, findSlot(next, chunkB.chunkId()));
    }

    @Test
    void applyReturnsNullWhenTargetSlotIsTooShortForOneChunk() {
        UUID taskId = UUID.randomUUID();

        TimeSlot longSlot = slot(9, 11);
        TimeSlot shortSlot = slot(11, 11, 30);

        TaskChunk longChunk = chunk(taskId, 0, 90, false);
        TaskChunk shortChunk = chunk(taskId, 1, 30, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(longChunk, longSlot),
                        new ScheduledChunk(shortChunk, shortSlot)
                )),
                new ArrayList<>()
        );

        SwapMove move = new SwapMove(new FixedRandom(List.of(0, 1)));

        SolverState next = move.apply(current);

        assertNull(next);
    }

    @Test
    void freeSlotsRemainUnchangedAfterSwap() {
        UUID taskId = UUID.randomUUID();

        TimeSlot slotA = slot(9, 10);
        TimeSlot slotB = slot(10, 11);
        TimeSlot freeSlot = slot(11, 12);

        TaskChunk chunkA = chunk(taskId, 0, 60, false);
        TaskChunk chunkB = chunk(taskId, 1, 60, false);

        SolverState current = new SolverState(
                new ArrayList<>(List.of(
                        new ScheduledChunk(chunkA, slotA),
                        new ScheduledChunk(chunkB, slotB)
                )),
                new ArrayList<>(List.of(freeSlot))
        );

        SwapMove move = new SwapMove(new FixedRandom(List.of(0, 1)));

        SolverState next = move.apply(current);

        assertNotNull(next);
        assertEquals(List.of(freeSlot), next.freeSlots());
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

    private static TimeSlot slot(int hour, int startMinute, int endMinute) {
        return new TimeSlot(
                LocalDate.of(2026, 4, 27),
                LocalTime.of(hour, startMinute),
                LocalTime.of(hour, endMinute)
        );
    }

    private static TaskChunk chunk(UUID taskId, int chunkIndex, int durationMinutes, boolean pinned) {
        return new TaskChunk(
                UUID.randomUUID(),
                taskId,
                chunkIndex,
                2,
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