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
    private static final LocalDate DAY = LocalDate.of(2026, 4, 27);

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(DAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static TaskChunk chunk(UUID taskId, int durationMinutes, boolean pinned) {
        return new TaskChunk(
                UUID.randomUUID(), taskId,
                0, 1, durationMinutes,
                0, 0, null,
                null, null, pinned, null
        );
    }

    private static SolverState state(List<ScheduledChunk> scheduled, List<TimeSlot> free) {
        return new SolverState(new ArrayList<>(scheduled), new ArrayList<>(free));
    }

    private static Random fixed(Integer... ints) {
        Queue<Integer> queue = new ArrayDeque<>(List.of(ints));
        return new Random() {
            @Override public int nextInt(int bound) {
                if (queue.isEmpty()) fail("Kein weiterer int-Wert konfiguriert");
                int v = queue.remove();
                if (v < 0 || v >= bound) fail("Wert " + v + " außerhalb bound " + bound);
                return v;
            }
        };
    }

    @Test
    void apply_swapsSlotsOfTwoUnpinnedChunks() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TaskChunk chunkB = chunk(taskId, 60, false);
        TimeSlot slot3 = slot(9, 10);
        TimeSlot slot7 = slot(11, 12);

        SolverState result = new SwapMove(fixed(0, 1)).apply(state(
                List.of(new ScheduledChunk(chunkA, slot3),
                        new ScheduledChunk(chunkB, slot7)),
                List.of()
        ));

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkA.chunkId())
                        && sc.slot().equals(slot7)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkB.chunkId())
                        && sc.slot().equals(slot3)));
    }

    @Test
    void apply_returnsNull_whenFewerThanTwoUnpinnedChunks() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);

        assertNull(new SwapMove(fixed()).apply(state(
                List.of(new ScheduledChunk(chunkA, slot(9, 10))),
                List.of()
        )));
    }

    @Test
    void apply_ignoresPinnedChunks() {
        UUID taskId = UUID.randomUUID();
        TaskChunk pinned   = chunk(taskId, 60, true);
        TaskChunk unpinned = chunk(taskId, 60, false);

        assertNull(new SwapMove(fixed()).apply(state(
                List.of(new ScheduledChunk(pinned,   slot(9, 10)),
                        new ScheduledChunk(unpinned, slot(11, 12))),
                List.of()
        )));
    }

    @Test
    void apply_returnsNull_whenTargetSlotTooShort() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TaskChunk chunkB = chunk(taskId, 30, false);

        TimeSlot longSlot  = slot(9, 10);   // 60 min
        TimeSlot shortSlot = new TimeSlot(DAY, LocalTime.of(11, 0), LocalTime.of(11, 30)); // 30 min

        assertNull(new SwapMove(fixed(0, 1)).apply(state(
                List.of(new ScheduledChunk(chunkA, longSlot),
                        new ScheduledChunk(chunkB, shortSlot)),
                List.of()
        )));
    }

    @Test
    void apply_doesNotChangeFreeSlots() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TaskChunk chunkB = chunk(taskId, 60, false);
        TimeSlot freeSlot = slot(14, 15);

        SolverState result = new SwapMove(fixed(0, 1)).apply(state(
                List.of(new ScheduledChunk(chunkA, slot(9, 10)),
                        new ScheduledChunk(chunkB, slot(11, 12))),
                List.of(freeSlot)
        ));

        assertNotNull(result);
        assertEquals(1, result.freeSlots().size());
        assertTrue(result.freeSlots().contains(freeSlot));
    }

    @Test
    void apply_allowsSwap_acrossDifferentTasks() {
        TaskChunk chunkA = chunk(UUID.randomUUID(), 60, false);
        TaskChunk chunkB = chunk(UUID.randomUUID(), 60, false);
        TimeSlot slotA = slot(9, 10);
        TimeSlot slotB = slot(11, 12);

        SolverState result = new SwapMove(fixed(0, 1)).apply(state(
                List.of(new ScheduledChunk(chunkA, slotA),
                        new ScheduledChunk(chunkB, slotB)),
                List.of()
        ));

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkA.chunkId())
                        && sc.slot().equals(slotB)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkB.chunkId())
                        && sc.slot().equals(slotA)));
    }

    @Test
    void apply_doesNotMutateOriginalState() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TaskChunk chunkB = chunk(taskId, 60, false);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, slot(9, 10)),
                        new ScheduledChunk(chunkB, slot(11, 12))),
                List.of()
        );
        int sizeBefore = current.scheduledChunks().size();
        new SwapMove(fixed(0, 1)).apply(current);
        assertEquals(sizeBefore, current.scheduledChunks().size());
    }
}