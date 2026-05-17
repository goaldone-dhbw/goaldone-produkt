package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.*;
import de.goaldone.backend.model.DayOfWeek;
import de.goaldone.backend.scheduler.types.model.*;
import de.goaldone.backend.service.ScheduleService;
import org.jspecify.annotations.NonNull;
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

    private final ScheduleService scheduleService = new ScheduleService(null, null, null, null);

    @Test
    void shouldGenerate_singleTaskInSingleSlot() {

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(slot(date.toLocalDate(), 9, 10));
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

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(
                slot(date.toLocalDate(), 9, 0,  9,  30),
                slot(date.toLocalDate(), 10,30, 11, 0));
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

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(slot(date.toLocalDate(), 9, 10));
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
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(
                slot(date.toLocalDate(), 9, 10),
                slot(date.toLocalDate(), 13, 14));

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
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(
                slot(date.toLocalDate(), 9, 10),
                slot(date.toLocalDate(), 13, 14));

        TaskResponse task1 = task(60, List.of(), null, date.toLocalDate().plusDays(5));
        TaskResponse task2 = task(60, List.of(), null, date.toLocalDate().plusDays(2));
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
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(
                slot(date.toLocalDate(), 9, 10),
                slot(date.toLocalDate(), 13, 14));

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
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        TimeSlot freeSlot = slot(date.toLocalDate().plusDays(0), 9, 10);
        TimeSlot occupiedSlot = slot(date.toLocalDate().plusDays(1), 9, 10);
        List<TimeSlot> availableSlots = List.of(freeSlot, occupiedSlot);

        TaskResponse task = task(60, List.of(), LocalDateTime.of(date.toLocalDate(), LocalTime.of(15, 0)), null);
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
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        List<TimeSlot> availableSlots = List.of(
                slot(date.toLocalDate().plusDays(0), 9, 10),
                slot(date.toLocalDate().plusDays(1), 9, 10));


        TaskResponse taskWithDeadline = task(60, List.of(), null, date.toLocalDate().plusDays(1));
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

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(slot(date.toLocalDate(), 9, 10));

        TaskResponse task = task(65);
        List<TaskResponse> tasks = List.of(task);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks().size()).isEqualTo(1);

        ScheduledChunk scheduledChunk = result.scheduledChunks().getFirst();
        assertThat(scheduledChunk.slot().durationMinutes()).isEqualTo(task.getDuration());
        assertThat(scheduledChunk.chunk().durationMinutes()).isEqualTo(task.getDuration());
    }

    @Test
    void shouldAddAutomatedBreak_whenChunkExceedsSlotWithinBuffer() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<TimeSlot> availableSlots = List.of(slot(date.toLocalDate(), 8, 0, 12, 0));
        TaskResponse task = task(120, List.of(), null, null, CognitiveLoad.HIGH);
        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, List.of(task), null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.scheduledChunks()).hasSize(2);

        ScheduledChunk scheduledTask = result.scheduledChunks().getFirst();
        ScheduledChunk automatedBreak = result.scheduledChunks().getLast();

        assertThat(scheduledTask.slot()).isEqualTo(new TimeSlot(date.toLocalDate(), LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertThat(automatedBreak.chunk().taskTitle()).isEqualTo("Automatische Pause");
        assertThat(automatedBreak.slot()).isEqualTo(new TimeSlot(date.toLocalDate(), LocalTime.of(10, 0), LocalTime.of(10, 15)));
    }

    @Test
    void shouldWarnFromUnscheduledTasks() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        TimeSlot slot1 = slot(date.toLocalDate().plusDays(0), 15, 16);
        TimeSlot slot2 = slot(date.toLocalDate().plusDays(1), 8, 9);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        TaskResponse task1 = task(60,  List.of(), LocalDateTime.of(date.toLocalDate(), LocalTime.of(15, 0)), null);
        TaskResponse task2 = task(60, List.of(task1.getId()), null, date.toLocalDate().plusDays(1));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledTasks().size()).isEqualTo(1);
        assertThat(result.scheduledChunks().getFirst().chunk().taskId()).isEqualTo(task1.getId());
    }

    @Test
    void shouldScheduleOnOneDay() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        TimeSlot slot1 = slot(date.toLocalDate().plusDays(0), 8, 17);
        TimeSlot slot2 = slot(date.toLocalDate().plusDays(1), 8, 17);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        TaskResponse task1 = task(120);
        TaskResponse task2 = task(60, List.of(task1.getId()));
        List<TaskResponse> tasks = List.of(task1, task2);

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        TimeSlot task1TimeSlot = new TimeSlot(date.toLocalDate(), LocalTime.of(8,  0), LocalTime.of(10, 0));
        TimeSlot task2TimeSlot = new TimeSlot(date.toLocalDate(), LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThat(result.scheduledChunks().getFirst().slot()).isEqualTo(task1TimeSlot);
        assertThat(result.scheduledChunks().getLast().slot()).isEqualTo(task2TimeSlot);
    }

    @Test
    void shouldChunkAndScheduleOneByOne_whenLargeTask() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        TimeSlot slot1 = slot(date.toLocalDate().plusDays(0), 8, 17);
        TimeSlot slot2 = slot(date.toLocalDate().plusDays(1), 8, 17);
        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        List<TaskResponse> tasks = List.of(task(600, List.of(),null, null, CognitiveLoad.MODERATE));

        List<WorkingTimeEntity> workingTime = List.of(working(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 17));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTime
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledTasks().size()).isEqualTo(0);

        List<TimeSlot> expected = getTimeSlots(date);

        for (int i = 0; i < expected.size(); i++) {
            assertThat(result.scheduledChunks().get(i).slot()).isEqualTo(expected.get(i));
        }
    }

    private @NonNull List<TimeSlot> getTimeSlots(LocalDateTime date) {
        LocalDate today = date.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);

        return List.of(
                new TimeSlot(today,    LocalTime.of(8,  0),  LocalTime.of(12,0)),    // 4h
                new TimeSlot(today,    LocalTime.of(12, 0),  LocalTime.of(12,15)),   // Pause
                new TimeSlot(today,    LocalTime.of(12, 15), LocalTime.of(16,15)),   // 4h
                new TimeSlot(today,    LocalTime.of(16, 15), LocalTime.of(16,30)),   // Pause
                new TimeSlot(today,    LocalTime.of(16, 30), LocalTime.of(17,0)),    // 0.5h
                new TimeSlot(tomorrow, LocalTime.of(8,  0),  LocalTime.of(9,30))     // 1,5h
        );
    }

    @Test
    void shouldScheduleTasks_whenTasksAreExtremelyLarge() {

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        List<WorkingTimeEntity> workingTimeEntities = List.of(working(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                18));

        TaskResponse task1 = task(900);
        TaskResponse task2 = task(180);
        List<TaskResponse> tasks = List.of(task1, task2);

        List<TimeSlot> availableSlots = new ArrayList<>(List.of());
        for (int i = 0; i < 5; i++) {
            availableSlots.add(slot(date.toLocalDate().plusDays(i), 8, 18));
        }


        SchedulingContext context = new SchedulingContext(
            date, availableSlots, tasks, null, workingTimeEntities
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.unscheduledTasks()).isEmpty();
    }


    @Test
    void shouldCreateSchedule_whenRecurringAppointments() {

        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));

        List<WorkingTimeEntity> workingTimeEntities = List.of(working(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                17));

        TaskResponse task1 = task(540, List.of(), null, null, CognitiveLoad.HIGH);
        List<TaskResponse> tasks = List.of(task1);

        List<Appointment> appointments = List.of(
                appointment()
        );

        List<TimeSlot> availableSlots = scheduleService.getAvailableTimeSlots(appointments, workingTimeEntities, date);

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, appointments, workingTimeEntities
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledTasks()).isEmpty();
    }

    @Test
    void shouldAddAutomatedPause_whenCognitiveLoadReached_HIGH() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<WorkingTimeEntity> workingTimeEntities = List.of(working(List.of(DayOfWeek.MONDAY), 18));
        List<TaskResponse> tasks = List.of(task(480,  List.of(), null, null, CognitiveLoad.HIGH));
        List<TimeSlot> availableSlots = new ArrayList<>(List.of(slot(date.toLocalDate(), 8, 18)));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTimeEntities
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledTasks()).isEmpty();

        List<TimeSlot> expectedSlots = new ArrayList<>();
        expectedSlots.add(slot(date.toLocalDate(), 8,  0,  10, 0));
        expectedSlots.add(slot(date.toLocalDate(), 10, 0,  10, 15));
        expectedSlots.add(slot(date.toLocalDate(), 10, 15, 12, 15));
        expectedSlots.add(slot(date.toLocalDate(), 12, 15, 12, 30));
        expectedSlots.add(slot(date.toLocalDate(), 12, 30, 14, 30));
        expectedSlots.add(slot(date.toLocalDate(), 14, 30, 14, 45));
        expectedSlots.add(slot(date.toLocalDate(), 14, 45, 16, 45));
        expectedSlots.add(slot(date.toLocalDate(), 16, 45, 17, 0));

        for (int i = 0; i < expectedSlots.size(); i++) {
            assertThat(result.scheduledChunks().get(i).slot()).isEqualTo(expectedSlots.get(i));
        }
    }

    @Test
    void shouldAddAutomatedPause_whenCognitiveLoadReached_MODERATE() {
        LocalDateTime date = LocalDateTime.of(LocalDate.of(2026, 5, 11),  LocalTime.of(7, 0));
        List<WorkingTimeEntity> workingTimeEntities = List.of(working(List.of(DayOfWeek.MONDAY), 18));
        List<TaskResponse> tasks = List.of(task(480,  List.of(), null, null, CognitiveLoad.MODERATE));
        List<TimeSlot> availableSlots = new ArrayList<>(List.of(slot(date.toLocalDate(), 8, 18)));

        SchedulingContext context = new SchedulingContext(
                date, availableSlots, tasks, null, workingTimeEntities
        );

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.unscheduledTasks()).isEmpty();

        List<TimeSlot> expectedSlots = new ArrayList<>();
        expectedSlots.add(slot(date.toLocalDate(), 8,  0,   12, 0)); // 4h
        expectedSlots.add(slot(date.toLocalDate(), 12, 0,   12, 15)); // Pause
        expectedSlots.add(slot(date.toLocalDate(), 12, 15,  16, 15)); // 4h
        expectedSlots.add(slot(date.toLocalDate(), 16, 15,  16, 30)); // Pause


        for (int i = 0; i < expectedSlots.size(); i++) {
            assertThat(result.scheduledChunks().get(i).slot()).isEqualTo(expectedSlots.get(i));
        }
    }

    private Appointment appointment() {
        return new Appointment()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .title("Pause")
                .isBreak(true)
                .appointmentType(AppointmentType.RECURRING)
                .startTime("12:00")
                .endTime("13:00")
                .createdAt(OffsetDateTime.now())
                .date(null)
                .rrule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");
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
