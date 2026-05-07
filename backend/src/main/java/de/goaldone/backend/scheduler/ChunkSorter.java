package de.goaldone.backend.scheduler;

import de.goaldone.backend.scheduler.types.model.TaskChunk;

import java.util.*;
import java.util.stream.Collectors;

public class ChunkSorter {

    /**
     * Validates that there are no circular dependencies in the task chunks.
     * Throws IllegalArgumentException if a cycle is detected.
     */
    public void validateNoCircularDependencies(List<TaskChunk> chunks) {
        // Build adjacency list: taskId -> list of tasks that depend on it
        Map<UUID, List<TaskChunk>> dependencyGraph = buildDependencyGraph(chunks);

        // Track visited state for DFS
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();

        // Check each task for cycles
        for (TaskChunk chunk : chunks) {
            if (!visited.contains(chunk.taskId())) {
                detectCycleDFS(chunk.taskId(), dependencyGraph, visiting, visited);
            }
        }
    }

    /**
     * Builds a dependency graph: taskId -> list of tasks that depend on it
     */
    private Map<UUID, List<TaskChunk>> buildDependencyGraph(List<TaskChunk> chunks) {
        Map<UUID, List<TaskChunk>> graph = new HashMap<>();

        // Initialize empty lists for all tasks
        for (TaskChunk chunk : chunks) {
            graph.put(chunk.taskId(), new ArrayList<>());
        }

        // Build reverse dependencies (who depends on whom)
        for (TaskChunk chunk : chunks) {
            if (chunk.dependsOnTaskIds() != null) {
                for (UUID dependencyId : chunk.dependsOnTaskIds()) {
                    graph.get(dependencyId).add(chunk);
                }
            }
        }

        return graph;
    }

    /**
     * DFS-based cycle detection
     */
    private void detectCycleDFS(UUID taskId, Map<UUID, List<TaskChunk>> graph,
                                Set<UUID> visiting, Set<UUID> visited) {
        visiting.add(taskId);

        List<TaskChunk> dependents = graph.get(taskId);
        if (dependents != null) {
            for (TaskChunk dependent : dependents) {
                UUID dependentId = dependent.taskId();

                if (visiting.contains(dependentId)) {
                    throw new IllegalArgumentException(
                            "Circular dependency detected involving task: " + dependentId);
                }

                if (!visited.contains(dependentId)) {
                    detectCycleDFS(dependentId, graph, visiting, visited);
                }
            }
        }

        visiting.remove(taskId);
        visited.add(taskId);
    }

    /**
     * Performs topological sort using Kahn's algorithm.
     * Tasks with no dependencies come first, then tasks that depend on them, etc.
     */
    public List<TaskChunk> topologicalSort(List<TaskChunk> chunks) {
        // First validate no cycles
        validateNoCircularDependencies(chunks);

        // Build graph: taskId -> tasks that depend on it
        Map<UUID, List<TaskChunk>> graph = buildDependencyGraph(chunks);

        // Calculate in-degrees (number of dependencies for each task)
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (TaskChunk chunk : chunks) {
            inDegree.put(chunk.taskId(), 0);
        }

        for (TaskChunk chunk : chunks) {
            if (chunk.dependsOnTaskIds() != null) {
                for (UUID depId : chunk.dependsOnTaskIds()) {
                    inDegree.put(chunk.taskId(), inDegree.get(chunk.taskId()) + 1);
                }
            }
        }

        // Queue for tasks with no dependencies (in-degree 0)
        Queue<UUID> queue = new LinkedList<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<UUID> sortedTaskIds = new ArrayList<>();

        while (!queue.isEmpty()) {
            UUID currentTaskId = queue.poll();
            sortedTaskIds.add(currentTaskId);

            // Reduce in-degree of all tasks that depend on current task
            List<TaskChunk> dependents = graph.get(currentTaskId);
            if (dependents != null) {
                for (TaskChunk dependent : dependents) {
                    UUID dependentId = dependent.taskId();
                    inDegree.put(dependentId, inDegree.get(dependentId) - 1);

                    // If in-degree becomes 0, add to queue
                    if (inDegree.get(dependentId) == 0) {
                        queue.add(dependentId);
                    }
                }
            }
        }

        // If not all tasks were processed, there was a cycle (shouldn't happen due to validation)
        if (sortedTaskIds.size() != chunks.size()) {
            throw new IllegalStateException("Topological sort failed - this shouldn't happen if validation passed");
        }

        // Convert back to TaskChunk list in sorted order
        Map<UUID, TaskChunk> taskMap = chunks.stream()
                .collect(Collectors.toMap(TaskChunk::taskId, chunk -> chunk));

        return sortedTaskIds.stream()
                .map(taskMap::get)
                .collect(Collectors.toList());
    }


}
