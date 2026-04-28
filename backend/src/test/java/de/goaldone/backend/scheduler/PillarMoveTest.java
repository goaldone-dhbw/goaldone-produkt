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
    private static final LocalDate DAY = LocalDate.of(2026, 4, 27);

    private static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(DAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static TaskChunk chunk(UUID taskId, int chunkIndex, int durationMinutes,
                                   boolean pinned, LocalDateTime notBefore, LocalDateTime deadline) {
        return new TaskChunk(
                UUID.randomUUID(), taskId,
                chunkIndex, 3, durationMinutes,
                0, 0, null,
                notBefore, deadline, pinned, null
        );
    }

    private static TaskChunk chunk(UUID taskId, int chunkIndex, int durationMinutes, boolean pinned) {
        return chunk(taskId, chunkIndex, durationMinutes, pinned, null, null);
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
    void constructor_throwsException_whenMaxShiftIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new PillarMove(new Random(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PillarMove(new Random(), -1));
    }

    @Test
    void apply_shiftsAllChunksOfTask_byPositiveN() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 0, 60, false);
        TaskChunk chunkB = chunk(taskId, 1, 60, false);
        TaskChunk chunkC = chunk(taskId, 2, 60, false);

        TimeSlot s8  = slot(8,  9);
        TimeSlot s9  = slot(9,  10);
        TimeSlot s10 = slot(10, 11);
        TimeSlot s11 = slot(11, 12);
        TimeSlot s12 = slot(12, 13);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s8),
                        new ScheduledChunk(chunkB, s9),
                        new ScheduledChunk(chunkC, s10)),
                List.of(s11, s12)
        );

        SolverState result = new PillarMove(fixed(0, 4), 2).apply(current);

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkA.chunkId()) && sc.slot().equals(s10)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkB.chunkId()) && sc.slot().equals(s11)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkC.chunkId()) && sc.slot().equals(s12)));
    }

    @Test
    void apply_preservesOrderAndSpacing() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 0, 60, false);
        TaskChunk chunkB = chunk(taskId, 1, 60, false);

        TimeSlot s8  = slot(8,  9);
        TimeSlot s9  = slot(9,  10);
        TimeSlot s10 = slot(10, 11);
        TimeSlot s11 = slot(11, 12);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s8),
                        new ScheduledChunk(chunkB, s10)),
                List.of(s9, s11)
        );

        SolverState result = new PillarMove(fixed(0, 2), 1).apply(current);

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkA.chunkId()) && sc.slot().equals(s9)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(chunkB.chunkId()) && sc.slot().equals(s11)));
    }

    @Test
    void apply_returnsNull_whenTargetIndexOutOfBounds() {
        UUID taskId = UUID.randomUUID();
        TaskChunk chunkA = chunk(taskId, 0, 60, false);
        TaskChunk chunkB = chunk(taskId, 1, 60, false);

        TimeSlot s8 = slot(8, 9);
        TimeSlot s9 = slot(9, 10);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s8),
                        new ScheduledChunk(chunkB, s9)),
                List.of()
        );

        assertNull(new PillarMove(fixed(0, 0), 3).apply(current));
    }

    @Test
    void apply_returnsNull_whenTargetSlotOccupiedByOtherTask() {
        UUID taskId      = UUID.randomUUID();
        UUID otherTaskId = UUID.randomUUID();

        TaskChunk chunkA = chunk(taskId,      0, 60, false);
        TaskChunk other  = chunk(otherTaskId, 0, 60, false);

        TimeSlot s8 = slot(8, 9);
        TimeSlot s9 = slot(9, 10); // belegt durch andere Task

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s8),
                        new ScheduledChunk(other,  s9)),
                List.of()
        );

        assertNull(new PillarMove(fixed(0, 2), 1).apply(current));
    }

    @Test
    void apply_returnsNull_whenTargetSlotExceedsDeadline() {
        UUID taskId = UUID.randomUUID();
        LocalDateTime deadline = LocalDateTime.of(DAY, LocalTime.of(10, 0)); // Ende spätestens 10:00

        TaskChunk chunkA = chunk(taskId, 0, 60, false, null, deadline);

        TimeSlot s8  = slot(8,  9);
        TimeSlot s10 = slot(10, 11); // endet 11:00 → nach deadline

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s8)),
                List.of(s10)
        );
        assertNull(new PillarMove(fixed(0, 2), 1).apply(current));
    }

    @Test
    void apply_returnsNull_whenTargetSlotBeforeNotBefore() {
        UUID taskId = UUID.randomUUID();
        LocalDateTime notBefore = LocalDateTime.of(DAY, LocalTime.of(10, 0));

        TaskChunk chunkA = chunk(taskId, 0, 60, false, notBefore, null);

        TimeSlot s8  = slot(8,  9);
        TimeSlot s9  = slot(9, 10);
        TimeSlot s10 = slot(10, 11);

        SolverState current = state(
                List.of(new ScheduledChunk(chunkA, s10)),
                List.of(s8, s9)
        );

        assertNull(new PillarMove(fixed(0, 0), 1).apply(current));
    }

    @Test
    void apply_doesNotMovePinnedChunks() {
        UUID taskId = UUID.randomUUID();
        TaskChunk pinned   = chunk(taskId, 0, 60, true);
        TaskChunk unpinned = chunk(taskId, 1, 60, false);

        TimeSlot s8  = slot(8,  9);
        TimeSlot s9  = slot(9,  10);
        TimeSlot s10 = slot(10, 11);

        SolverState current = state(
                List.of(new ScheduledChunk(pinned,   s8),
                        new ScheduledChunk(unpinned, s9)),
                List.of(s10)
        );

        SolverState result = new PillarMove(fixed(0, 2), 1).apply(current);

        assertNotNull(result);
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(pinned.chunkId())
                        && sc.slot().equals(s8)));
        assertTrue(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().chunkId().equals(unpinned.chunkId())
                        && sc.slot().equals(s10)));
    }
}