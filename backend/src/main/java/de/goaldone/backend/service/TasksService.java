package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TasksService {

    private final TaskRepository taskRepository;

    @Transactional
    public TaskResponse createTask(UUID accountId, TaskCreateRequest request) {
        validateCreateRequest(request);

        TaskEntity entity = new TaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId(accountId);
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(request.getDescription());
        entity.setDuration(request.getDuration());
        entity.setDeadline(toInstant(request.getDeadline()));
        entity.setStatus(request.getStatus() == null ? TaskStatus.OPEN : request.getStatus());

        TaskEntity saved = taskRepository.save(entity);
        applyDependencies(saved, request.getDependencyIds(), accountId, true);

        TaskEntity persisted = taskRepository.findByIdAndAccountId(saved.getId(), accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return toResponse(persisted);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(UUID accountId) {
        return taskRepository.findAllByAccountIdOrderByIdAsc(accountId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public TaskResponse updateTask(UUID accountId, UUID id, TaskUpdateRequest request) {
        TaskEntity task = taskRepository.findByIdAndAccountId(id, accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
            }
            task.setTitle(request.getTitle().trim());
        }

        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }

        if (request.getDuration() != null) {
            if (request.getDuration() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duration must be greater than 0");
            }
            task.setDuration(request.getDuration());
        }

        if (request.getDeadline() != null) {
            task.setDeadline(toInstant(request.getDeadline()));
        }

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        if (request.getDependencyIds() != null) {
            applyDependencies(task, request.getDependencyIds(), accountId, true);
        }

        TaskEntity persisted = taskRepository.save(task);
        return toResponse(persisted);
    }

    @Transactional
    public void deleteTask(UUID accountId, UUID id) {
        TaskEntity task = taskRepository.findByIdAndAccountId(id, accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        taskRepository.delete(task);
    }

    private void validateCreateRequest(TaskCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        if (request.getDuration() == null || request.getDuration() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duration must be greater than 0");
        }
    }

    private void applyDependencies(TaskEntity task, List<UUID> dependencyIds, UUID accountId, boolean allowEmptyToClear) {
        if (dependencyIds == null) {
            return;
        }

        Set<UUID> uniqueDependencyIds = new LinkedHashSet<>(dependencyIds);

        if (task.getId() != null && uniqueDependencyIds.contains(task.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task cannot depend on itself");
        }

        if (uniqueDependencyIds.isEmpty()) {
            if (allowEmptyToClear) {
                task.getDependencies().clear();
                taskRepository.save(task);
            }
            return;
        }

        List<TaskEntity> dependencies = taskRepository.findAllByIdInAndAccountId(uniqueDependencyIds, accountId);
        if (dependencies.size() != uniqueDependencyIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dependency task not found for current user");
        }

        task.setDependencies(new LinkedHashSet<>(dependencies));
        assertNoDependencyCycle(task, accountId);
        taskRepository.save(task);
    }

    private void assertNoDependencyCycle(TaskEntity changedTask, UUID accountId) {
        List<TaskEntity> allTasks = taskRepository.findAllByAccountIdOrderByIdAsc(accountId);
        Map<UUID, Set<UUID>> graph = new HashMap<>();

        for (TaskEntity task : allTasks) {
            graph.put(task.getId(), task.getDependencies().stream().map(TaskEntity::getId).collect(Collectors.toSet()));
        }

        graph.put(changedTask.getId(), changedTask.getDependencies().stream().map(TaskEntity::getId).collect(Collectors.toSet()));

        if (containsCycle(graph, changedTask.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cyclic task dependency is not allowed");
        }
    }

    private boolean containsCycle(Map<UUID, Set<UUID>> graph, UUID startId) {
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        return dfsHasCycle(startId, graph, visiting, visited);
    }

    private boolean dfsHasCycle(UUID current, Map<UUID, Set<UUID>> graph, Set<UUID> visiting, Set<UUID> visited) {
        if (visiting.contains(current)) {
            return true;
        }
        if (visited.contains(current)) {
            return false;
        }

        visiting.add(current);
        for (UUID next : graph.getOrDefault(current, Set.of())) {
            if (dfsHasCycle(next, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }

    private TaskResponse toResponse(TaskEntity entity) {
        TaskResponse response = new TaskResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setDuration(entity.getDuration());
        response.setDeadline(toOffsetDateTime(entity.getDeadline()));
        response.setStatus(entity.getStatus());

        List<UUID> dependencyIds = entity.getDependencies().stream()
            .map(TaskEntity::getId)
            .sorted()
            .toList();
        response.setDependencyIds(dependencyIds);
        return response;
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
