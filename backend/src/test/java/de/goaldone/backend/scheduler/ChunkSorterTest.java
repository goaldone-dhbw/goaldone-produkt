package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.TaskChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkSorterTest {

    private ChunkSorter chunkSorter;

    @BeforeEach
    void setUp() {
        chunkSorter = new ChunkSorter();
    }

    @Test
    void validateNoCircularDependencies_shouldPassForIndependentTasks() {
        TaskChunk task1 = task("A");
        TaskChunk task2 = task("B");

        assertDoesNotThrow(() ->
                chunkSorter.validateNoCircularDependencies(List.of(task1, task2))
        );
    }

    @Test
    void validateNoCircularDependencies_shouldPassForLinearDependencies() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();

        TaskChunk a = task(taskA);
        TaskChunk b = task(taskB, List.of(taskA));
        TaskChunk c = task(taskC, List.of(taskB));

        assertDoesNotThrow(() ->
                chunkSorter.validateNoCircularDependencies(List.of(a, b, c))
        );
    }

    @Test
    void validateNoCircularDependencies_shouldThrowForSimpleCycle() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();

        TaskChunk a = task(taskA, List.of(taskB));
        TaskChunk b = task(taskB, List.of(taskA));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chunkSorter.validateNoCircularDependencies(List.of(a, b))
        );

        assertTrue(exception.getMessage().contains("Circular dependency detected"));
    }

    @Test
    void validateNoCircularDependencies_shouldThrowForComplexCycle() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();

        TaskChunk a = task(taskA, List.of(taskC));
        TaskChunk b = task(taskB, List.of(taskA));
        TaskChunk c = task(taskC, List.of(taskB));

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkSorter.validateNoCircularDependencies(List.of(a, b, c))
        );
    }

    @Test
    void topologicalSort_shouldReturnTasksInCorrectOrder() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();

        TaskChunk a = task(taskA);
        TaskChunk b = task(taskB, List.of(taskA));
        TaskChunk c = task(taskC, List.of(taskB));

        List<TaskChunk> result = chunkSorter.topologicalSort(List.of(c, b, a));

        assertEquals(3, result.size());

        assertEquals(taskA, result.get(0).taskId());
        assertEquals(taskB, result.get(1).taskId());
        assertEquals(taskC, result.get(2).taskId());
    }

    @Test
    void topologicalSort_shouldHandleMultipleIndependentChains() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();
        UUID taskD = UUID.randomUUID();

        TaskChunk a = task(taskA);
        TaskChunk b = task(taskB, List.of(taskA));

        TaskChunk c = task(taskC);
        TaskChunk d = task(taskD, List.of(taskC));

        List<TaskChunk> result = chunkSorter.topologicalSort(List.of(d, c, b, a));

        assertEquals(4, result.size());

        int indexA = indexOf(result, taskA);
        int indexB = indexOf(result, taskB);

        int indexC = indexOf(result, taskC);
        int indexD = indexOf(result, taskD);

        assertTrue(indexA < indexB);
        assertTrue(indexC < indexD);
    }

    @Test
    void topologicalSort_shouldThrowForCycle() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();

        TaskChunk a = task(taskA, List.of(taskB));
        TaskChunk b = task(taskB, List.of(taskA));

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkSorter.topologicalSort(List.of(a, b))
        );
    }

    @Test
    void topologicalSort_shouldHandleTaskWithMultipleDependencies() {
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();

        TaskChunk a = task(taskA);
        TaskChunk b = task(taskB);

        TaskChunk c = task(taskC, List.of(taskA, taskB));

        List<TaskChunk> result = chunkSorter.topologicalSort(List.of(c, a, b));

        int indexA = indexOf(result, taskA);
        int indexB = indexOf(result, taskB);
        int indexC = indexOf(result, taskC);

        assertTrue(indexA < indexC);
        assertTrue(indexB < indexC);
    }

    // ---------- helper methods ----------

    private TaskChunk task(String seed) {
        return task(UUID.nameUUIDFromBytes(seed.getBytes()));
    }

    private TaskChunk task(UUID taskId) {
        return task(taskId, null);
    }

    private TaskChunk task(UUID taskId, List<UUID> dependencies) {
        return TaskChunk.builder()
                .chunkId(UUID.randomUUID())
                .taskId(taskId)
                .chunkIndex(0)
                .totalChunks(1)
                .durationMinutes(60)
                .topologicalLevel(0)
                .slackMinutes(0)
                .isPinned(false)
                .dependsOnTaskIds(dependencies)
                .build();
    }

    private int indexOf(List<TaskChunk> tasks, UUID taskId) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).taskId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }
}
