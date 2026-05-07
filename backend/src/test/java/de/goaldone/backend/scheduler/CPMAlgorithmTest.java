package de.goaldone.backend.scheduler;

import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CPMAlgorithmTest {

    @Mock
    private TaskSorter taskSorter;

    @Mock
    private Chunker chunker;

    @Spy
    @InjectMocks
    private CPMAlgorithm algorithm;

    @Test
    void shouldGenerateInitialScheduleWithoutSplittingChunks() {

        UUID taskId = UUID.randomUUID();

        TaskResponse task = mock(TaskResponse.class);
        when(task.getId()).thenReturn(taskId);

        List<TaskResponse> tasks = List.of(task);

        TimeSlot freeSlot = new TimeSlot(
                LocalDate.now(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0)
        );

        List<TimeSlot> availableSlots = List.of(freeSlot);

        SchedulingContext context = new SchedulingContext(
                LocalDate.of(2026, 5, 11), // Tuesday
                availableSlots,
                tasks,
                List.of()
        );

        TaskChunk chunk = mock(TaskChunk.class);

        ScheduledChunk scheduledChunk = new ScheduledChunk(chunk, freeSlot);

        doReturn(List.of())
                .when(algorithm)
                .calculateSlack(tasks);

        when(taskSorter.sort(any(), any()))
                .thenReturn(List.of(taskId));

        when(chunker.chunkTasks(tasks, context.workingTimes()))
                .thenReturn(Map.of(taskId, List.of(chunk)));

        doReturn(List.of(scheduledChunk))
                .when(algorithm)
                .findTimeSlotForChunk(chunk, availableSlots);

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks()).hasSize(1);
        assertThat(result.scheduledChunks().getFirst())
                .isEqualTo(scheduledChunk);

        assertThat(result.freeSlots())
                .containsExactly(freeSlot);

        verify(algorithm, never()).updateChunks(any());
    }

    @Test
    void shouldUpdateChunksWhenChunkWasSplit() {

        UUID taskId = UUID.randomUUID();

        TaskResponse task = mock(TaskResponse.class);
        when(task.getId()).thenReturn(taskId);

        List<TaskResponse> tasks = List.of(task);

        TimeSlot slot1 = new TimeSlot(
                LocalDate.now(),
                LocalTime.of(9, 0),
                LocalTime.of(9, 30)
        );

        TimeSlot slot2 = new TimeSlot(
                LocalDate.now(),
                LocalTime.of(9, 30),
                LocalTime.of(10, 0)
        );

        List<TimeSlot> availableSlots = List.of(slot1, slot2);

        SchedulingContext context = new SchedulingContext(
                LocalDate.now(),
                availableSlots,
                tasks,
                List.<WorkingTimeEntity>of()
        );

        TaskChunk chunk = mock(TaskChunk.class);

        ScheduledChunk splitChunk1 = new ScheduledChunk(chunk, slot1);
        ScheduledChunk splitChunk2 = new ScheduledChunk(chunk, slot2);

        ScheduledChunk updatedChunk1 = new ScheduledChunk(chunk, slot1);
        ScheduledChunk updatedChunk2 = new ScheduledChunk(chunk, slot2);

        doReturn(List.of())
                .when(algorithm)
                .calculateSlack(tasks);

        when(taskSorter.sort(any(), any()))
                .thenReturn(List.of(taskId));

        when(chunker.chunkTasks(tasks, context.workingTimes()))
                .thenReturn(Map.of(taskId, List.of(chunk)));

        doReturn(List.of(splitChunk1, splitChunk2))
                .when(algorithm)
                .findTimeSlotForChunk(chunk, availableSlots);

        doReturn(List.of(updatedChunk1, updatedChunk2))
                .when(algorithm)
                .updateChunks(List.of(splitChunk1, splitChunk2));

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result).isNotNull();
        assertThat(result.scheduledChunks())
                .containsExactly(updatedChunk1, updatedChunk2);

        verify(algorithm).updateChunks(List.of(splitChunk1, splitChunk2));
    }

    @Test
    void shouldProcessTasksInSortedOrder() {

        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();

        TaskResponse task1 = mock(TaskResponse.class);
        TaskResponse task2 = mock(TaskResponse.class);

        when(task1.getId()).thenReturn(taskId1);
        when(task2.getId()).thenReturn(taskId2);

        List<TaskResponse> tasks = List.of(task1, task2);

        TimeSlot slot = new TimeSlot(
                LocalDate.now(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0)
        );

        List<TimeSlot> availableSlots = List.of(slot);

        SchedulingContext context = new SchedulingContext(
                LocalDate.now(),
                availableSlots,
                tasks,
                List.<WorkingTimeEntity>of()
        );

        TaskChunk chunk1 = mock(TaskChunk.class);
        TaskChunk chunk2 = mock(TaskChunk.class);

        ScheduledChunk scheduledChunk1 = new ScheduledChunk(chunk1, slot);
        ScheduledChunk scheduledChunk2 = new ScheduledChunk(chunk2, slot);

        doReturn(List.of())
                .when(algorithm)
                .calculateSlack(tasks);

        when(taskSorter.sort(any(), any()))
                .thenReturn(List.of(taskId2, taskId1));

        when(chunker.chunkTasks(tasks, context.workingTimes()))
                .thenReturn(Map.of(
                        taskId1, List.of(chunk1),
                        taskId2, List.of(chunk2)
                ));

        doReturn(List.of(scheduledChunk1))
                .when(algorithm)
                .findTimeSlotForChunk(chunk1, availableSlots);

        doReturn(List.of(scheduledChunk2))
                .when(algorithm)
                .findTimeSlotForChunk(chunk2, availableSlots);

        SolverState result = algorithm.generateInitialSchedule(context);

        assertThat(result.scheduledChunks())
                .containsExactly(scheduledChunk2, scheduledChunk1);
    }
}
