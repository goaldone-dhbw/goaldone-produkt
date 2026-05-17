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

class DependencyConstraintTest {

    private DependencyConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new DependencyConstraint();
    }

    @Test
    void shouldNotViolate_whenDependencyEndsBeforeDependentStarts() {
        UUID depTaskId = UUID.randomUUID();
        ScheduledChunk dep = chunkForTask(depTaskId, 9, 10, List.of());
        ScheduledChunk dependent = chunkForTask(UUID.randomUUID(), 11, 12, List.of(depTaskId));
        SolverState state = new SolverState(List.of(dep, dependent), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldNotViolate_whenDependencyEndsExactlyWhenDependentStarts() {
        UUID depTaskId = UUID.randomUUID();
        ScheduledChunk dep = chunkForTask(depTaskId, 9, 10, List.of());
        ScheduledChunk dependent = chunkForTask(UUID.randomUUID(), 10, 11, List.of(depTaskId));
        SolverState state = new SolverState(List.of(dep, dependent), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldViolate_whenDependencyEndsAfterDependentStarts() {
        UUID depTaskId = UUID.randomUUID();
        ScheduledChunk dep = chunkForTask(depTaskId, 9, 11, List.of());
        ScheduledChunk dependent = chunkForTask(UUID.randomUUID(), 10, 12, List.of(depTaskId));
        SolverState state = new SolverState(List.of(dep, dependent), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    @Test
    void shouldViolate_whenDependencyIsNotScheduled() {
        UUID unscheduledDepId = UUID.randomUUID();
        ScheduledChunk dependent = chunkForTask(UUID.randomUUID(), 10, 11, List.of(unscheduledDepId));
        SolverState state = new SolverState(List.of(dependent), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    @Test
    void shouldNotViolate_whenDependencyListIsEmpty() {
        ScheduledChunk chunk = chunkForTask(UUID.randomUUID(), 9, 10, List.of());
        SolverState state = new SolverState(List.of(chunk), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldNotViolate_whenDependencyListIsNull() {
        ScheduledChunk chunk = chunkForTask(UUID.randomUUID(), 9, 10, null);
        SolverState state = new SolverState(List.of(chunk), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isFalse();
    }

    @Test
    void shouldViolate_whenDependencyHasMultipleChunks_andLastEndsAfterStart() {
        UUID depTaskId = UUID.randomUUID();
        ScheduledChunk depChunk1 = chunkForTask(depTaskId, 9, 10, List.of());
        ScheduledChunk depChunk2 = chunkForTask(depTaskId, 11, 12, List.of());
        ScheduledChunk dependent = chunkForTask(UUID.randomUUID(), 10, 11, List.of(depTaskId));
        SolverState state = new SolverState(List.of(depChunk1, depChunk2, dependent), List.of(), List.of());

        constraint.updateConstraint(state);
        assertThat(constraint.isViolated).isTrue();
    }

    // --- Helpers ---

    private static final LocalDate DATE = LocalDate.of(2026, 5, 11);

    private static ScheduledChunk chunkForTask(UUID taskId, int startHour, int endHour, List<UUID> dependsOn) {
        TaskChunk tc = TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(taskId)
                .taskTitle("Test")
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes((endHour - startHour) * 60)
                .cognitiveLoad(CognitiveLoad.MODERATE)
                .dependsOnTaskIds(dependsOn)
                .build();
        TimeSlot slot = new TimeSlot(DATE, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
        return new ScheduledChunk(tc, slot);
    }
}

