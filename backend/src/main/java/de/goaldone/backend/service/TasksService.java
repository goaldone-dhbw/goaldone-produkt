package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskAccountListResponse;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing tasks.
 * This service handles business logic for tasks, including validation, persistence, and complex
 * logic like checking for dependency cycles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TasksService {

    private final TaskRepository taskRepository;

    /**
     * Creates a new task based on the provided request.
     *
     * @param request The {@link TaskCreateRequest} containing details of the task to be created.
     * @return The created {@link TaskResponse}.
     */
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

        checkDependenciesAndApply(entity, request.getDependencyIds(), accountId, true);

        TaskEntity saved = taskRepository.save(entity);
        return toResponse(saved);
    }

    /**
     * Retrieves all tasks for a specific account ID.
     *
     * @param accountId The UUID of the account for which tasks are being requested.
     * @return A list of {@link TaskResponse} objects.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksForAccountId(UUID accountId) {
        return taskRepository.findAllByAccountIdOrderByIdAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves all tasks for the specified accounts.
     *
     * @param accountIds The account IDs to retrieve tasks for.
     * @return A list of {@link TaskAccountListResponse} objects, each representing an account and its tasks.
     */
    @Transactional(readOnly = true)
    public List<TaskAccountListResponse> getTasksForAllAccounts(List<UUID> accountIds) {
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

    /**
     * Retrieves tasks filtered and sorted.
     *
     * @param accountIds    The accessible account IDs.
     * @param status        The {@link TaskStatus} to filter by.
     * @param cognitiveLoad The {@link CognitiveLoad} to filter by.
     * @param deadlineFrom  The start of the deadline range.
     * @param deadlineTo    The end of the deadline range.
     * @param minDuration   The minimum estimated duration in minutes.
     * @param maxDuration   The maximum estimated duration in minutes.
     * @param sortBy        The property to sort by.
     * @param sortDirection The sort direction ("asc" or "desc").
     * @param searchTerm    The search term to match against task title and description.
     * @return A list of matching {@link TaskResponse} objects.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(List<UUID> accountIds, TaskStatus status, CognitiveLoad cognitiveLoad,
                                       LocalDateTime deadlineFrom, LocalDateTime deadlineTo,
                                       Integer minDuration, Integer maxDuration,
                                       String sortBy, String sortDirection, String searchTerm) {

        if (deadlineFrom != null && deadlineTo != null && deadlineFrom.isAfter(deadlineTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Das Startdatum muss vor oder gleich dem Enddatum liegen");
        }

        if (accountIds.isEmpty()) {
            return List.of();
        }

        Specification<TaskEntity> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("dependencies", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("accountId").in(accountIds));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (cognitiveLoad != null) {
                predicates.add(cb.equal(root.get("cognitiveLoad"), cognitiveLoad));
            }
            if (deadlineFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), deadlineFrom.toInstant(ZoneOffset.UTC)));
            }
            if (deadlineTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("deadline"), deadlineTo.toInstant(ZoneOffset.UTC)));
            }
            if (minDuration != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("duration"), minDuration));
            }
            if (maxDuration != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("duration"), maxDuration));
            }
            if (searchTerm != null && !searchTerm.isBlank()) {
                String pattern = "%" + searchTerm.toLowerCase() + "%";
                Predicate titleLike = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleLike, descLike));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort.Direction direction = Sort.Direction.ASC;
        if ("desc".equalsIgnoreCase(sortDirection)) {
            direction = Sort.Direction.DESC;
        }

        Set<String> allowedSortProperties = Set.of("id", "deadline", "duration", "status", "cognitiveLoad", "title", "accountId");
        String sortProperty = "id";
        if (sortBy != null && allowedSortProperties.contains(sortBy)) {
            sortProperty = sortBy;
        }
        Sort sort = Sort.by(direction, sortProperty);

        return taskRepository.findAll(spec, sort)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Updates an existing task with the specified ID.
     *
     * @param id                 The UUID of the task to update.
     * @param request            The {@link TaskUpdateRequest} containing updated details for the task.
     * @param accessibleAccounts The account IDs the user has access to.
     * @return The updated {@link TaskResponse}.
     */
    @Transactional
    public TaskResponse updateTask(UUID id, TaskUpdateRequest request, List<UUID> accessibleAccounts) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!accessibleAccounts.contains(task.getAccountId())) {
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

        checkDependenciesAndApply(task, request.getDependencyIds(), task.getAccountId(), true);

        TaskEntity persisted = taskRepository.save(task);
        return toResponse(persisted);
    }

    /**
     * Deletes the task with the specified ID if the user has access to the account it belongs to.
     *
     * @param id                 The UUID of the task to delete.
     * @param accessibleAccounts The account IDs the user has access to.
     */
    @Transactional
    public void deleteTask(UUID id, List<UUID> accessibleAccounts) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!accessibleAccounts.contains(task.getAccountId())) {
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

    private void checkDependenciesAndApply(TaskEntity task, List<UUID> dependencyIds, UUID accountId, boolean allowEmptyToClear) {
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
                .accountId(entity.getAccountId())
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
