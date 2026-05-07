package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.TaskSlack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskSorterTest {

    private TaskSorter sorter;

    @BeforeEach
    void setUp() {
        sorter = new TaskSorter();
    }

    @Test
    void sort_shouldReturnEmptyList_whenNoTasksExist() {

        List<UUID> result = sorter.sort(List.of(), List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void sort_shouldSortTasksByNumberOfDependents() {

        UUID rootId = UUID.randomUUID();
        UUID dependent1Id = UUID.randomUUID();
        UUID dependent2Id = UUID.randomUUID();

        TaskResponse root = task(rootId, CognitiveLoad.LOW);

        TaskResponse dependent1 = task(
                dependent1Id,
                CognitiveLoad.LOW,
                List.of(rootId)
        );

        TaskResponse dependent2 = task(
                dependent2Id,
                CognitiveLoad.LOW,
                List.of(rootId)
        );

        List<UUID> result = sorter.sort(
                List.of(dependent1, dependent2, root),
                List.of()
        );

        assertEquals(rootId, result.getFirst());
    }

    @Test
    void sort_shouldSortBySlack_whenDependentCountIsEqual() {

        UUID task1Id = UUID.randomUUID();
        UUID task2Id = UUID.randomUUID();

        TaskResponse task1 = task(task1Id, CognitiveLoad.LOW);
        TaskResponse task2 = task(task2Id, CognitiveLoad.LOW);

        TaskSlack slack1 = slack(task1Id, 500);
        TaskSlack slack2 = slack(task2Id, 100);

        List<UUID> result = sorter.sort(
                List.of(task1, task2),
                List.of(slack1, slack2)
        );

        assertEquals(task2Id, result.get(0));
        assertEquals(task1Id, result.get(1));
    }

    @Test
    void sort_shouldSortByCognitiveLoad_whenSlackIsEqual() {

        UUID highId = UUID.randomUUID();
        UUID moderateId = UUID.randomUUID();
        UUID lowId = UUID.randomUUID();

        TaskResponse high = task(highId, CognitiveLoad.HIGH);
        TaskResponse moderate = task(moderateId, CognitiveLoad.MODERATE);
        TaskResponse low = task(lowId, CognitiveLoad.LOW);

        List<TaskSlack> slacks = List.of(
                slack(highId, 100),
                slack(moderateId, 100),
                slack(lowId, 100)
        );

        List<UUID> result = sorter.sort(
                List.of(low, moderate, high),
                slacks
        );

        assertEquals(highId, result.get(0));
        assertEquals(moderateId, result.get(1));
        assertEquals(lowId, result.get(2));
    }

    @Test
    void sort_shouldPrioritizeDependentsOverSlackAndCognitiveLoad() {

        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        UUID taskCId = UUID.randomUUID();

        TaskResponse taskA = task(
                taskAId,
                CognitiveLoad.LOW
        );

        TaskResponse taskB = task(
                taskBId,
                CognitiveLoad.LOW,
                List.of(taskAId)
        );

        TaskResponse taskC = task(
                taskCId,
                CognitiveLoad.HIGH
        );

        List<TaskSlack> slacks = List.of(
                slack(taskAId, 9999),
                slack(taskBId, 9999),
                slack(taskCId, 1)
        );

        List<UUID> result = sorter.sort(
                List.of(taskA, taskB, taskC),
                slacks
        );

        // task1 task first because another task depends on it
        assertEquals(taskCId, result.getFirst());
        assertEquals(taskAId, result.get(1));
        assertEquals(taskBId, result.get(2));
    }

    @Test
    void sort_shouldUseMaxSlack_whenSlackIsMissing() {

        UUID noSlackId = UUID.randomUUID();
        UUID withSlackId = UUID.randomUUID();

        TaskResponse noSlack = task(noSlackId, CognitiveLoad.HIGH);
        TaskResponse withSlack = task(withSlackId, CognitiveLoad.LOW);

        List<UUID> result = sorter.sort(
                List.of(noSlack, withSlack),
                List.of(slack(withSlackId, 100))
        );

        assertEquals(withSlackId, result.get(0));
        assertEquals(noSlackId, result.get(1));
    }

    @Test
    void sort_shouldThrowExceptionForCircularDependencies() {

        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();

        TaskResponse taskA = task(
                taskAId,
                CognitiveLoad.LOW,
                List.of(taskBId)
        );

        TaskResponse taskB = task(
                taskBId,
                CognitiveLoad.LOW,
                List.of(taskAId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> sorter.sort(List.of(taskA, taskB), List.of())
        );
    }

    @Test
    void sort_shouldHandleTasksWithoutDependencies() {

        UUID task1Id = UUID.randomUUID();
        UUID task2Id = UUID.randomUUID();
        UUID task3Id = UUID.randomUUID();

        TaskResponse task1 = task(task1Id, CognitiveLoad.LOW);
        TaskResponse task2 = task(task2Id, CognitiveLoad.MODERATE);
        TaskResponse task3 = task(task3Id, CognitiveLoad.HIGH);

        List<TaskSlack> slacks = List.of(
                slack(task1Id, 300),
                slack(task2Id, 200),
                slack(task3Id, 100)
        );

        List<UUID> result = sorter.sort(
                List.of(task1, task2, task3),
                slacks
        );

        assertEquals(task3Id, result.get(0));
        assertEquals(task2Id, result.get(1));
        assertEquals(task1Id, result.get(2));
    }

    @Test
    void validateNoCircularDependencies_shouldThrowException_whenCircularDependencyExists() {

        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        UUID taskCId = UUID.randomUUID();
        UUID taskDId = UUID.randomUUID();

        TaskResponse taskA = task(taskAId, CognitiveLoad.LOW, List.of(taskBId));
        TaskResponse taskB = task(taskBId, CognitiveLoad.LOW, List.of(taskCId, taskDId));
        TaskResponse taskC = task(taskCId, CognitiveLoad.LOW);
        TaskResponse taskD = task(taskDId, CognitiveLoad.LOW, List.of(taskAId));

        assertThrows(
                IllegalArgumentException.class,
                () -> sorter.validateNoCircularDependencies(List.of(taskA, taskB, taskC, taskD))
        );
    }

    @Test
    void buildDependencyGraph_shouldWorkForDeeperDependencies() {
        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        UUID taskCId = UUID.randomUUID();
        UUID taskDId = UUID.randomUUID();

        TaskResponse taskA = task(taskAId, CognitiveLoad.LOW);
        TaskResponse taskB = task(taskBId, CognitiveLoad.LOW, List.of(taskAId));
        TaskResponse taskC = task(taskCId, CognitiveLoad.LOW, List.of(taskBId));
        TaskResponse taskD = task(taskDId, CognitiveLoad.LOW, List.of(taskCId));
        List<TaskResponse> tasks = List.of(taskA, taskB, taskC, taskD);

        Map<UUID, List<UUID>> result = sorter.buildDependencyGraph(tasks);

        assertEquals(0, result.get(taskAId).size());
        assertEquals(1, result.get(taskBId).size());
        assertEquals(1, result.get(taskCId).size());
        assertEquals(1, result.get(taskDId).size());
    }

    // ---------- helpers ----------

    private TaskResponse task(UUID id, CognitiveLoad load) {
        return task(id, load, List.of());
    }

    private TaskResponse task(
            UUID id,
            CognitiveLoad load,
            List<UUID> dependencies
    ) {

        TaskResponse task = new TaskResponse();

        task.setId(id);
        task.setCognitiveLoad(load);
        task.setDependencyIds(dependencies);

        return task;
    }

    private TaskSlack slack(UUID taskId, long slackMinutes) {
        return new TaskSlack(taskId, null, null, null, null, slackMinutes);
    }
}