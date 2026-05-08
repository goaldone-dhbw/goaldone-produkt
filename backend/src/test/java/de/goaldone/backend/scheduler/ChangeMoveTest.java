package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import de.goaldone.backend.scheduler.types.moves.ChangeMove;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeMoveTest {
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
    void apply_movesUnpinnedChunkToFreeSlot() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TimeSlot occupied = slot(9, 10);
        TimeSlot free     = slot(11, 12);

        SolverState result = new ChangeMove(fixed(0, 0)).apply(state(
                List.of(new ScheduledChunk(chunkA, occupied)),
                List.of(free)
        ));

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkA.chunkId())
                        && sc.slot().equals(free)));
    }

    @Test
    void apply_returnsNull_whenAllChunksPinned() {
        UUID taskId = UUID.randomUUID();
        TaskChunk pinned = chunk(taskId, 60, true);

        assertNull(new ChangeMove(fixed()).apply(state(
                List.of(new ScheduledChunk(pinned, slot(9, 10))),
                List.of(slot(11, 12))
        )));
    }

    @Test
    void apply_returnsNull_whenNoFreeSlots() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);

        assertNull(new ChangeMove(fixed()).apply(state(
                List.of(new ScheduledChunk(chunkA, slot(9, 10))),
                List.of()
        )));
    }

    @Test
    void apply_returnsNull_whenTargetSlotTooShort() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false); // braucht 60 min

        TimeSlot thirtyMin = new TimeSlot(DAY, LocalTime.of(11, 0), LocalTime.of(11, 30));

        assertNull(new ChangeMove(fixed(0, 0)).apply(state(
                List.of(new ScheduledChunk(chunkA, slot(9, 10))),
                List.of(thirtyMin)
        )));
    }

    @Test
    void apply_freesOldSlot_andRemovesNewSlot() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot newSlot = slot(11, 12);

        SolverState result = new ChangeMove(fixed(0, 0)).apply(state(
                List.of(new ScheduledChunk(chunkA, oldSlot)),
                List.of(newSlot)
        ));

        assertNotNull(result);
        assertTrue(result.freeSlots().contains(oldSlot));
        assertFalse(result.freeSlots().contains(newSlot));
    }

    @Test
    void apply_doesNotMutateOriginalState() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 60, false);
        TimeSlot oldSlot = slot(9, 10);
        TimeSlot newSlot = slot(11, 12);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, oldSlot)),
                List.of(newSlot)
        );
        int scheduledBefore = current.scheduledChunks().size();
        int freeBefore      = current.freeSlots().size();

        new ChangeMove(fixed(0, 0)).apply(current);

        assertEquals(scheduledBefore, current.scheduledChunks().size());
        assertEquals(freeBefore,      current.freeSlots().size());
    }
}