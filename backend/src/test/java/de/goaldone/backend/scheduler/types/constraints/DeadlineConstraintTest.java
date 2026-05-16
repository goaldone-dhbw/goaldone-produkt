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

class DeadlineConstraintTest {

    private DeadlineConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new DeadlineConstraint();
    }

    @Test
    void shouldNotViolate_whenChunkEndsBeforeDeadline() {
        SolverState state = stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 17, 0)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldNotViolate_whenChunkEndsExactlyAtDeadline() {
        SolverState state = stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 10, 0)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldViolate_whenChunkEndsAfterDeadline() {
        SolverState state = stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 9, 30)));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    @Test
    void shouldNotViolate_whenDeadlineIsNull() {
        SolverState state = stateWith(chunk(9, 10, null));
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldViolate_whenOneOfMultipleChunksExceedsDeadline() {
        ScheduledChunk ok = scheduledChunk(9, 10, LocalDateTime.of(2026, 5, 11, 17, 0));
        ScheduledChunk bad = scheduledChunk(14, 16, LocalDateTime.of(2026, 5, 11, 15, 0));
        SolverState state = new SolverState(List.of(ok, bad), List.of(), List.of());
        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    @Test
    void shouldResetViolation_onSubsequentCall() {
        constraint.updateConstraint(stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 9, 30))));
        assertThat(constraint.isViolated).isTrue();

        constraint.updateConstraint(stateWith(chunk(9, 10, LocalDateTime.of(2026, 5, 11, 17, 0))));
        assertThat(constraint.isViolated).isFalse();
    }

    // --- Helpers ---

    private static final LocalDate DATE = LocalDate.of(2026, 5, 11);

    private SolverState stateWith(ScheduledChunk chunk) {
        return new SolverState(List.of(chunk), List.of(), List.of());
    }

    private ScheduledChunk chunk(int startHour, int endHour, LocalDateTime deadline) {
        return scheduledChunk(startHour, endHour, deadline);
    }

    private static ScheduledChunk scheduledChunk(int startHour, int endHour, LocalDateTime deadline) {
        TaskChunk tc = TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .taskTitle("Test")
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes((endHour - startHour) * 60)
                .cognitiveLoad(CognitiveLoad.MODERATE)
                .deadline(deadline)
                .dependsOnTaskIds(List.of())
                .build();
        TimeSlot slot = new TimeSlot(DATE, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
        return new ScheduledChunk(tc, slot);
    }
}

