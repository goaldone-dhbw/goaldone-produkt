package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskAccountListResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final UserIdentityService userIdentityService;

    @Transactional
    public TaskResponse createTask(TaskCreateRequest request) {
        validateCreateRequest(request);

        var accountId = request.getAccountId();

        TaskEntity entity = new TaskEntity(
                UUID.randomUUID(),
                accountId,
                request.getTitle().trim(),
                request.getDescription(),
                request.getDuration(),
                toInstant(request.getDeadline()),
                request.getStatus(),
                request.getCognitiveLoad(),
                toInstant(request.getDontScheduleBefore()),
                request.getCustomChunkSize(),
                new LinkedHashSet<>()
        );

        applyDependencies(entity, request.getDependencyIds(), accountId, true);

        TaskEntity saved = taskRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksForAccountId(Jwt jwt, UUID accountId) {
        boolean hasUserAccess = userIdentityService.hasUserAccessToAccount(jwt, accountId);

        return taskRepository.findAllByAccountIdOrderByIdAsc(accountId)
                .stream()
                .filter(task -> hasUserAccess)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskAccountListResponse> getTasksForAllAccounts(Jwt jwt) {
        List<UUID> accountIds = userIdentityService.accountIdsForUser(jwt);

        Map<UUID, List<TaskEntity>> tasksByAccount = taskRepository.findAllByAccountIdIn(accountIds).stream()
                .collect(Collectors.groupingBy(TaskEntity::getAccountId));

        return accountIds.stream()
                .map(accountId -> new TaskAccountListResponse(accountId)
                        .tasks(tasksByAccount.getOrDefault(accountId, List.of())
                                .stream()
                                .map(this::toResponse)
                                .toList()))
                .toList();
    }

    @Transactional
    public TaskResponse updateTask(Jwt jwt, UUID id, TaskUpdateRequest request) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!userIdentityService.hasUserAccessToAccount(jwt, task.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the task's account");
        }

        validateUpdateRequest(request);

        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setDuration(request.getDuration());
        task.setDeadline(toInstant(request.getDeadline()));
        task.setStatus(request.getStatus());
        task.setCognitiveLoad(request.getCognitiveLoad());
        task.setDontScheduleBefore(toInstant(request.getDontScheduleBefore()));
        task.setCustomChunkSize(request.getCustomChunkSize());

        applyDependencies(task, request.getDependencyIds(), task.getAccountId(), true);

        TaskEntity persisted = taskRepository.save(task);
        return toResponse(persisted);
    }

    @Transactional
    public void deleteTask(UUID id, Jwt jwt) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        List<UUID> accountsWithAccess = userIdentityService.accountIdsForUser(jwt);
        if (!accountsWithAccess.contains(task.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the task's account");
        }
        taskRepository.delete(task);
    }

    private void validateCreateRequest(TaskCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.getAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required");
        }
        validateCommonFields(request.getTitle(), request.getDuration(), request.getStatus(),
                request.getCognitiveLoad(), request.getCustomChunkSize());
    }

    private void validateUpdateRequest(TaskUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        validateCommonFields(request.getTitle(), request.getDuration(), request.getStatus(),
                request.getCognitiveLoad(), request.getCustomChunkSize());
    }

    private void validateCommonFields(String title, Integer duration, TaskStatus status,
                                      CognitiveLoad cognitiveLoad, Integer customChunkSize) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        if (duration == null || duration <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duration must be greater than 0");
        }
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        if (cognitiveLoad == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cognitiveLoad is required");
        }
        if (customChunkSize != null && customChunkSize <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customChunkSize must be greater than 0");
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
            }
            return;
        }

        List<TaskEntity> dependencies = taskRepository.findAllByIdInAndAccountId(uniqueDependencyIds, accountId);
        if (dependencies.size() != uniqueDependencyIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dependency task not found for current user");
        }

        task.setDependencies(new LinkedHashSet<>(dependencies));
        assertNoDependencyCycle(task, accountId);
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
        List<UUID> dependencyIds = entity.getDependencies().stream()
                .map(TaskEntity::getId)
                .sorted()
                .toList();

        return new TaskResponse()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .duration(entity.getDuration())
                .deadline(toOffsetDateTime(entity.getDeadline()))
                .status(entity.getStatus())
                .cognitiveLoad(entity.getCognitiveLoad())
                .dontScheduleBefore(toOffsetDateTime(entity.getDontScheduleBefore()))
                .customChunkSize(entity.getCustomChunkSize())
                .dependencyIds(dependencyIds);
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
