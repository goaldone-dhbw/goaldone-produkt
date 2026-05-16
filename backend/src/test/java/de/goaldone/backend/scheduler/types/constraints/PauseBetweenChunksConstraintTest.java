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

class PauseBetweenChunksConstraintTest {

    private PauseBetweenChunksConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new PauseBetweenChunksConstraint();
    }

    @Test
    void shouldBeActive_whenPauseBetweenChunksIsSufficient() {
        ScheduledChunk c1 = chunk(DATE, 9, 0, 10, 0);
        ScheduledChunk c2 = chunk(DATE, 10, 15, 11, 0);
        constraint.updateConstraint(new SolverState(List.of(c1, c2), List.of(), List.of()));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldNotBeActive_whenPauseIsLessThan15Minutes() {
        ScheduledChunk c1 = chunk(DATE, 9, 0, 10, 0);
        ScheduledChunk c2 = chunk(DATE, 10, 10, 11, 0);
        constraint.updateConstraint(new SolverState(List.of(c1, c2), List.of(), List.of()));
        assertThat(constraint.isActive).isFalse();
    }

    @Test
    void shouldNotBeActive_whenChunksAreBackToBack() {
        ScheduledChunk c1 = chunk(DATE, 9, 0, 10, 0);
        ScheduledChunk c2 = chunk(DATE, 10, 0, 11, 0);
        constraint.updateConstraint(new SolverState(List.of(c1, c2), List.of(), List.of()));
        assertThat(constraint.isActive).isFalse();
    }

    @Test
    void shouldBeActive_whenOnlyOneChunk() {
        ScheduledChunk c1 = chunk(DATE, 9, 0, 10, 0);
        constraint.updateConstraint(new SolverState(List.of(c1), List.of(), List.of()));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldBeActive_whenScheduleIsEmpty() {
        constraint.updateConstraint(new SolverState(List.of(), List.of(), List.of()));
        assertThat(constraint.isActive).isTrue();
    }

    @Test
    void shouldBeActive_whenChunksAreOnDifferentDays() {
        ScheduledChunk c1 = chunk(DATE, 16, 0, 17, 0);
        ScheduledChunk c2 = chunk(DATE.plusDays(1), 9, 0, 10, 0);
        constraint.updateConstraint(new SolverState(List.of(c1, c2), List.of(), List.of()));
        assertThat(constraint.isActive).isTrue();
    }

    // --- Helpers ---

    private static final LocalDate DATE = LocalDate.of(2026, 5, 11);

    private static ScheduledChunk chunk(LocalDate date, int startH, int startM, int endH, int endM) {
        int duration = (endH * 60 + endM) - (startH * 60 + startM);
        TaskChunk tc = TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .taskTitle("Test")
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes(duration)
                .cognitiveLoad(CognitiveLoad.MODERATE)
                .dependsOnTaskIds(List.of())
                .build();
        TimeSlot slot = new TimeSlot(date, LocalTime.of(startH, startM), LocalTime.of(endH, endM));
        return new ScheduledChunk(tc, slot);
    }
}

