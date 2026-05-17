package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PauseAfterReachedCognitiveLoadConstraintTest {

    private PauseAfterReachedCognitiveLoadConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new PauseAfterReachedCognitiveLoadConstraint();
    }

    @Test
    void shouldBeActive_whenHighThresholdReachedAndPauseFollows() {
        // 120 min HIGH, then 30 min pause, then next chunk
        ScheduledChunk c1 = chunk(9, 0, 11, 0, CognitiveLoad.HIGH);
        ScheduledChunk c2 = chunk(11, 30, 12, 0, CognitiveLoad.LOW);
        constraint.updateConstraint(state(c1, c2));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldNotBeActive_whenHighThresholdReachedAndNoPause() {
        // 120 min HIGH, immediately followed by next chunk
        ScheduledChunk c1 = chunk(9, 0, 11, 0, CognitiveLoad.HIGH);
        ScheduledChunk c2 = chunk(11, 0, 12, 0, CognitiveLoad.LOW);
        constraint.updateConstraint(state(c1, c2));
        assertThat(constraint.isActive).isFalse();
    }

    @Test
    void shouldNotBeActive_whenAccumulatedHighExceedsThresholdWithoutPause() {
        // 2x 60 min HIGH back-to-back = 120 min, then immediate next chunk
        ScheduledChunk c1 = chunk(9, 0, 10, 0, CognitiveLoad.HIGH);
        ScheduledChunk c2 = chunk(10, 0, 11, 0, CognitiveLoad.HIGH);
        ScheduledChunk c3 = chunk(11, 0, 12, 0, CognitiveLoad.LOW);
        constraint.updateConstraint(state(c1, c2, c3));
        assertThat(constraint.isActive).isFalse();
    }

    @Test
    void shouldNotBeActive_whenModerateThresholdReachedWithoutPause() {
        // 240 min MODERATE back-to-back, then immediate next chunk
        ScheduledChunk c1 = chunk(8, 0, 10, 0, CognitiveLoad.MODERATE);
        ScheduledChunk c2 = chunk(10, 0, 12, 0, CognitiveLoad.MODERATE);
        ScheduledChunk c3 = chunk(12, 0, 13, 0, CognitiveLoad.LOW);
        constraint.updateConstraint(state(c1, c2, c3));
        assertThat(constraint.isActive).isFalse();
    }

    @Test
    void shouldBeActive_whenOnlyLowChunks() {
        ScheduledChunk c1 = chunk(8, 0, 12, 0, CognitiveLoad.LOW);
        ScheduledChunk c2 = chunk(12, 0, 16, 0, CognitiveLoad.LOW);
        constraint.updateConstraint(state(c1, c2));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldBeActive_whenThresholdReachedOnLastChunkOfDay() {
        // 120 min HIGH as the only/last chunk – no following chunk to need a pause
        ScheduledChunk c1 = chunk(9, 0, 11, 0, CognitiveLoad.HIGH);
        constraint.updateConstraint(state(c1));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldBeActive_whenCognitiveLoadIsNull() {
        ScheduledChunk c1 = chunk(9, 0, 11, 0, null);
        ScheduledChunk c2 = chunk(11, 0, 12, 0, null);
        constraint.updateConstraint(state(c1, c2));
        assertThat(constraint.isActive).isTrue();
    }

    // --- Helpers ---

    private static final LocalDate DATE = LocalDate.of(2026, 5, 11);

    private static SolverState state(ScheduledChunk... chunks) {
        return new SolverState(List.of(chunks), List.of(), List.of(), null);
    }

    private static ScheduledChunk chunk(int startH, int startM, int endH, int endM, CognitiveLoad load) {
        int duration = (endH * 60 + endM) - (startH * 60 + startM);
        TaskChunk tc = TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .taskTitle("Test")
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes(duration)
                .cognitiveLoad(load)
                .dependsOnTaskIds(List.of())
                .build();
        TimeSlot slot = new TimeSlot(DATE, LocalTime.of(startH, startM), LocalTime.of(endH, endM));
        return new ScheduledChunk(tc, slot);
    }
}

