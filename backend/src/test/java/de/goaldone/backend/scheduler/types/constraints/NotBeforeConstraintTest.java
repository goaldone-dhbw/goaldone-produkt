package de.goaldone.backend.scheduler.types.constraints;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.scheduler.types.model.ScheduledChunk;
import de.goaldone.backend.scheduler.types.model.SolverState;
import de.goaldone.backend.scheduler.types.model.TaskChunk;
import de.goaldone.backend.scheduler.types.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotBeforeConstraintTest {

    private NotBeforeConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new NotBeforeConstraint();
    }

    @Test
    void shouldNotViolate_whenChunkStartsAfterNotBefore() {
        SolverState state = stateWith(chunk(10, 11, LocalDateTime.of(2026, 5, 11, 9, 0)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldNotViolate_whenChunkStartsExactlyAtNotBefore() {
        SolverState state = stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 9, 0)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldViolate_whenChunkStartsBeforeNotBefore() {
        SolverState state = stateWith(chunk(8, 9, LocalDateTime.of(2026, 5, 11, 9, 0)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    @Test
    void shouldNotViolate_whenNotBeforeIsNull() {
        SolverState state = stateWith(chunk(8, 9, null));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    // --- Helpers ---

    private static final LocalDate DATE = LocalDate.of(2026, 5, 11);

    private SolverState stateWith(ScheduledChunk chunk) {
        return new SolverState(List.of(chunk), List.of(), List.of(), null);
    }

    private ScheduledChunk chunk(int startHour, int endHour, LocalDateTime notBefore) {
        TaskChunk tc = TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .taskTitle("Test")
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes((endHour - startHour) * 60)
                .cognitiveLoad(CognitiveLoad.MODERATE)
                .notBefore(notBefore)
                .dependsOnTaskIds(List.of())
                .build();
        TimeSlot slot = new TimeSlot(DATE, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
        return new ScheduledChunk(tc, slot);
    }
}

