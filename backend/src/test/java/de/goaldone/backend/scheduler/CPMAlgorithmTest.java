package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.scheduler.types.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static de.goaldone.backend.scheduler.SchedulerTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CPMAlgorithmTest {

    @Spy
    @InjectMocks
    private CPMAlgorithm algorithm;

    @Test
    void shouldGenerate_singleTaskInSingleSlot() {

        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(slot(date, 9, 10));
        List<TaskResponse> tasks = List.of(task(60));
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 16));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(1);
        assertThat(result.freeSlots()).isEmpty();
    }

    @Test
    void shouldSplitInChunks_whenNoTimeSlotBigEnough() {

        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(
                slot(date, 9, 0,  9,  30),
                slot(date, 10,30, 11, 0));
        List<TaskResponse> tasks = List.of(task(60));
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 16));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(2);
        assertThat(result.freeSlots()).isEmpty();

        verify(algorithm).updateChunks(any());
    }

    @Test
    void shouldSplitTimeSlot_whenTaskDoesntFillSlot() {

        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(slot(date, 9, 10));
        List<TaskResponse> tasks = List.of(
                task(30),
                task(30));
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 16));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.scheduledChunks().size() == 2);
        assertThat(result.freeSlots().isEmpty());
    }

    @Test
    void shouldGenerateInOrder_byDependencies() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(
                slot(date, 9, 10),
                slot(date, 13, 14));

        TaskResponse task1 = task(60);
        TaskResponse task2 = task(60, List.of(task1.getId()));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);
        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(2);

        assertThat(result.scheduledChunks().get(0).chunk().taskId()).isEqualTo(task1.getId());
        assertThat(result.scheduledChunks().get(1).chunk().taskId()).isEqualTo(task2.getId());
    }

    @Test
    void shouldGenerateInOrder_bySlack() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(
                slot(date, 9, 10),
                slot(date, 13, 14));

        TaskResponse task1 = task(60, List.of(), null, date.plusDays(5));
        TaskResponse task2 = task(60, List.of(), null, date.plusDays(2));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);
        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(2);

        assertThat(result.scheduledChunks().get(0).chunk().taskId()).isEqualTo(task2.getId());
        assertThat(result.scheduledChunks().get(1).chunk().taskId()).isEqualTo(task1.getId());
    }

    @Test
    void shouldGenerateInOrder_byCognitiveLoad() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(
                slot(date, 9, 10),
                slot(date, 13, 14));

        TaskResponse task1 = task(60, List.of(), null, null, CognitiveLoad.LOW);
        TaskResponse task2 = task(60, List.of(), null, null, CognitiveLoad.HIGH);

        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);
        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(2);

        assertThat(result.scheduledChunks().get(0).chunk().taskId()).isEqualTo(task2.getId());
        assertThat(result.scheduledChunks().get(1).chunk().taskId()).isEqualTo(task1.getId());
    }

    @Test
    void shouldNotScheduleBefore() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday

        TimeSlot freeSlot = slot(date.plusDays(0), 9, 10);
        TimeSlot occupiedSlot = slot(date.plusDays(1), 9, 10);
        List<TimeSlot> availableSlots = List.of(freeSlot, occupiedSlot);

        TaskResponse task = task(60, List.of(), LocalDateTime.of(date, LocalTime.of(15, 0)), null);
        List<TaskResponse> tasks = List.of(task);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(1);
        assertThat(result.freeSlots()).hasSize(1);

        assertThat(result.freeSlots().getFirst()).isEqualTo(freeSlot);
        assertThat(result.scheduledChunks().getFirst().slot()).isEqualTo(occupiedSlot);
    }

    @Test
    void shouldScheduleBeforeDeadline() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday

        List<TimeSlot> availableSlots = List.of(
                slot(date.plusDays(0), 9, 10),
                slot(date.plusDays(1), 9, 10));


        TaskResponse taskWithDeadline = task(60, List.of(), null, date.plusDays(1));
        TaskResponse taskWithoutDeadline = task(60);

        List<TaskResponse> tasks = List.of(taskWithoutDeadline, taskWithDeadline);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.scheduledChunks().getFirst().chunk().taskId()).isEqualTo(taskWithDeadline.getId());
        assertThat(result.scheduledChunks().getLast().chunk().taskId()).isEqualTo(taskWithoutDeadline.getId());
    }

    @Test
    void shouldNotSplit_whenLittleTimeLeft() {
        // Should append the 5 minutes to the time slot

        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        List<TimeSlot> availableSlots = List.of(slot(date, 9, 10));
        TaskResponse task = task(65);
        List<TaskResponse> tasks = List.of(task);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.scheduledChunks().size()).isEqualTo(1);

        ScheduledChunk scheduledChunk = result.scheduledChunks().getFirst();
        assertThat(scheduledChunk.chunk().durationMinutes()).isEqualTo(task.getDuration());
        assertThat(scheduledChunk.chunk().durationMinutes()).isEqualTo(scheduledChunk.slot().durationMinutes());

    }

    @Test
    void shouldWarnFromUnscheduledTasks() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday

        TimeSlot slot1 = slot(date.plusDays(0), 15, 16);
        TimeSlot slot2 = slot(date.plusDays(1), 8, 9);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        TaskResponse task1 = task(60,  List.of(), LocalDateTime.of(date, LocalTime.of(15, 0)), null);
        TaskResponse task2 = task(60, List.of(task1.getId()), null, date.plusDays(1));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledChunks().size()).isEqualTo(1);
        assertThat(result.scheduledChunks().getFirst().chunk().taskId()).isEqualTo(task1.getId());
    }

    @Test
    void shouldScheduleOnOneDay() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday

        TimeSlot slot1 = slot(date.plusDays(0), 8, 17);
        TimeSlot slot2 = slot(date.plusDays(1), 8, 17);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        TaskResponse task1 = task(120);
        TaskResponse task2 = task(60, List.of(task1.getId()));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        TimeSlot task1TimeSlot = new TimeSlot(date, LocalTime.of(8,  0), LocalTime.of(10, 0));
        TimeSlot task2TimeSlot = new TimeSlot(date, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(result.scheduledChunks().getFirst().slot()).isEqualTo(task1TimeSlot);
        assertThat(result.scheduledChunks().getLast().slot()).isEqualTo(task2TimeSlot);
    }

    @Test
    void shouldChunkAndScheduleOneByOne_whenLargeTask() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday

        TimeSlot slot1 = slot(date.plusDays(0), 8, 17);
        TimeSlot slot2 = slot(date.plusDays(1), 8, 17);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        List<TaskResponse> tasks = List.of(task(600, List.of(),null, null, CognitiveLoad.MODERATE));

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledChunks().size()).isEqualTo(0);


        List<TimeSlot> expected = List.of(
                new TimeSlot(date, LocalTime.of(8, 0), LocalTime.of(12,0)),
                new TimeSlot(date, LocalTime.of(12, 0), LocalTime.of(16,0)),
                new TimeSlot(date.plusDays(1), LocalTime.of(8, 0), LocalTime.of(10,0))
        );

        for (int i = 0; i < expected.size(); i++) {
            assertThat(result.scheduledChunks().get(i).slot()).isEqualTo(expected.get(i));
        }
    }

    @Test
    void shouldScheduleTasks_whenTasksAreExtremelyLarge() {

        LocalDate date = LocalDate.of(2026, 5, 11);

        List<WorkingTimeEntity> workingTimeEntities = List.of(working(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                18));

        TaskResponse task1 = task(900);
        TaskResponse task2 = task(180);
        List<TaskResponse> tasks = List.of(task1, task2);

        List<TimeSlot> availableSlots = new ArrayList<>(List.of());
        for (int i = 0; i < 5; i++) {
            availableSlots.add(slot(date.plusDays(i), 8, 18));
        }


        SchedulingContext context = new SchedulingContext(
            date, availableSlots, tasks, null, workingTimeEntities
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.unscheduledChunks()).isEmpty();
    }


    // -------------------------------------------------------------------------
    // tryScheduleUnscheduled – Tests
    // -------------------------------------------------------------------------

    @Test
    void tryScheduleUnscheduled_shouldDoNothing_whenNoUnscheduled() {
        LocalDate date = LocalDate.of(2026, 5, 11);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));
        SchedulingContext context = new SchedulingContext(date, List.of(), List.of(), null, workingTime);

        SolverState state = new SolverState(List.of(), List.of(), List.of(), context);

        SolverState result = algorithm.tryScheduleUnscheduled(state, context);

        assertThat(result).isSameAs(state);
    }

    @Test
    void tryScheduleUnscheduled_shouldReschedule_whenSlotsFreedUp() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        TaskResponse task = task(60);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        // Keine Slots → Task landet in unscheduledChunks
        SchedulingContext context = new SchedulingContext(date, List.of(), List.of(task), null, workingTime);
        SolverState initial = algorithm.generateInitialSchedule(context);
        assertThat(initial.unscheduledChunks()).hasSize(1);

        // Simuliert einen Move, der einen Slot freigemacht hat
        TimeSlot freedSlot = slot(date, 9, 10);
        SolverState stateWithFreeSlot = new SolverState(
                initial.scheduledChunks(),
                new ArrayList<>(List.of(freedSlot)),
                initial.unscheduledChunks(),
                context
        );

        SolverState result = algorithm.tryScheduleUnscheduled(stateWithFreeSlot, context);

        assertThat(result.unscheduledChunks()).isEmpty();
        assertThat(result.scheduledChunks()).hasSize(1);
        assertThat(result.scheduledChunks().getFirst().chunk().taskId()).isEqualTo(task.getId());
    }

    @Test
    void tryScheduleUnscheduled_shouldRetainUnscheduled_whenStillNotFitting() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        TaskResponse task = task(120); // braucht 120 min
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(date, List.of(), List.of(task), null, workingTime);
        SolverState initial = algorithm.generateInitialSchedule(context);
        assertThat(initial.unscheduledChunks()).hasSize(1);

        // Freigemachter Slot ist zu klein (nur 30 min)
        TimeSlot tooSmallSlot = slot(date, 9, 0, 9, 30);
        SolverState stateWithSmallSlot = new SolverState(
                initial.scheduledChunks(),
                new ArrayList<>(List.of(tooSmallSlot)),
                initial.unscheduledChunks(),
                context
        );

        SolverState result = algorithm.tryScheduleUnscheduled(stateWithSmallSlot, context);

        assertThat(result.unscheduledChunks()).hasSize(1);
        assertThat(result.scheduledChunks()).isEmpty();
    }

    @Test
    void tryScheduleUnscheduled_shouldPreservePreviouslyScheduledChunks() {
        LocalDate date = LocalDate.of(2026, 5, 11); // Monday
        TaskResponse alreadyScheduledTask = task(60);
        TaskResponse unscheduledTask = task(60);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        // Ersten Task normal einplanen
        TimeSlot slotForFirst = slot(date, 9, 10);
        SchedulingContext contextForFirst = new SchedulingContext(
                date, List.of(slotForFirst), List.of(alreadyScheduledTask), null, workingTime);
        SolverState firstScheduled = algorithm.generateInitialSchedule(contextForFirst);
        assertThat(firstScheduled.scheduledChunks()).hasSize(1);

        // Zweiten Task ist noch unscheduled – jetzt einen weiteren Slot hinzufügen
        TimeSlot freedSlot = slot(date, 10, 11);
        SchedulingContext contextWithBoth = new SchedulingContext(
                date, List.of(slotForFirst, freedSlot),
                List.of(alreadyScheduledTask, unscheduledTask), null, workingTime);

        UnscheduledTask unscheduled = new UnscheduledTask(
                unscheduledTask.getId(), unscheduledTask.getTitle(), null);
        SolverState stateWithUnscheduled = new SolverState(
                new ArrayList<>(firstScheduled.scheduledChunks()),
                new ArrayList<>(List.of(freedSlot)),
                new ArrayList<>(List.of(unscheduled)),
                contextWithBoth
        );

        SolverState result = algorithm.tryScheduleUnscheduled(stateWithUnscheduled, contextWithBoth);

        assertThat(result.unscheduledChunks()).isEmpty();
        assertThat(result.scheduledChunks()).hasSize(2);
        assertThat(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().taskId().equals(alreadyScheduledTask.getId()))).isTrue();
        assertThat(result.scheduledChunks().stream()
                .anyMatch(sc -> sc.chunk().taskId().equals(unscheduledTask.getId()))).isTrue();
    }

}
