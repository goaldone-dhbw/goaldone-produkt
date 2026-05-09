package de.goaldone.backend.scheduler;

import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.scheduler.types.model.TaskSlack;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TaskSorter {

    /**
     * Validates that there are no circular dependencies in the task chunks.
     * Throws IllegalArgumentException if a cycle is detected.
     */
    public void validateNoCircularDependencies(List<TaskResponse> tasks) {
        // Build adjacency list: taskId -> list of tasks that depend on it
        Map<UUID, List<UUID>> dependencyGraph = buildDependencyGraph(tasks);

        // Track visited state for DFS
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();

        // Check each task for cycles
        for (TaskResponse task : tasks) {
            if (!visited.contains(task.getId())) {
                detectCycleDFS(task.getId(), dependencyGraph, visiting, visited);
            }
        }
    }

    /**
     * Builds a dependency graph: taskId -> list of tasks that it depends on
     * Example:
     * - A depends on B.
     * - B depends on C and D.
     * Result:
     * A: [B]
     * B: [C, D]
     * C: []
     * D: []
     */
    public Map<UUID, List<UUID>> buildDependencyGraph(List<TaskResponse> tasks) {
        Map<UUID, List<UUID>> graph = new HashMap<>();

        // Initialize empty lists for all tasks
        for (TaskResponse task : tasks) {
            graph.put(task.getId(), new ArrayList<>());
        }

        // For each task, add its dependencies
        for (TaskResponse task : tasks) {
            if (task.getDependencyIds() != null) {
                for (UUID dependencyId : task.getDependencyIds()) {
                    graph.get(task.getId()).add(dependencyId);
                }
            }
        }

        return graph;
    }

    /**
     * DFS-based cycle detection
     */
    private void detectCycleDFS(UUID taskId, Map<UUID, List<UUID>> graph,
                                Set<UUID> visiting, Set<UUID> visited) {
        visiting.add(taskId);

        List<UUID> dependencies = graph.get(taskId);
        if (dependencies != null) {
            for (UUID dependencyId : dependencies) {

                if (visiting.contains(dependencyId)) {
                    throw new IllegalArgumentException(
                            "Circular dependency detected involving task: " + dependencyId);
                }

                if (!visited.contains(dependencyId)) {
                    detectCycleDFS(dependencyId, graph, visiting, visited);
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
    public List<UUID> sort(List<TaskResponse> tasks, List<TaskSlack> taskSlacks) {

        validateNoCircularDependencies(tasks);

        // TaskId -> tasks that depend on it
        Map<UUID, List<UUID>> dependencyGraph = buildDependencyGraph(tasks);

        Map<UUID, TaskResponse> taskMap = buildTaskMap(tasks);
        Map<UUID, Long> slackMap = buildSlackMap(taskSlacks);

        return dependencyGraph.entrySet().stream()

                // 1. Tasks with most dependents first
                .sorted(Comparator
                        .comparingInt((Map.Entry<UUID, List<UUID>> entry) ->
                                entry.getValue().size())

                        // 2. Lowest slack first
                        .thenComparing(entry ->
                                slackMap.getOrDefault(entry.getKey(), Long.MAX_VALUE))

                        // 3. Highest cognitive load first
                        .thenComparing(
                                (Map.Entry<UUID, List<UUID>> entry) ->
                                        getCognitiveLoadWeight(taskMap.get(entry.getKey())),
                                Comparator.reverseOrder()
                        )
                )
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<UUID, TaskResponse> buildTaskMap(List<TaskResponse> tasks) {
        return tasks.stream()
                .collect(Collectors.toMap(
                        TaskResponse::getId,
                        Function.identity()
                ));
    }

    private Map<UUID, Long> buildSlackMap(List<TaskSlack> taskSlacks) {
        return taskSlacks.stream()
                .collect(Collectors.toMap(
                        TaskSlack::taskId,
                        TaskSlack::slackMinutes
                ));
    }

    private int getCognitiveLoadWeight(TaskResponse task) {

        if (task == null || task.getCognitiveLoad() == null) {
            return 0;
        }

        return switch (task.getCognitiveLoad()) {
            case HIGH -> 2;
            case MODERATE -> 1;
            case LOW -> 0;
        };
    }
}

