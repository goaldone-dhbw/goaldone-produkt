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

/**
 * Service for managing tasks.
 * This service handles business logic for tasks, including validation, persistence, and complex
 * logic like checking for dependency cycles and user access to memberships (accounts) within an organization.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TasksService {

    private final TaskRepository taskRepository;
    private final UserService userService;

    /**
     * Creates a new task based on the provided request and the user's JWT within an organization.
     *
     * @param request The {@link TaskCreateRequest} containing details of the task to be created.
     * @param jwt     The {@link Jwt} representing the authenticated user.
     * @param xOrgID  The UUID of the organization.
     * @return The created {@link TaskResponse}.
     */
    @Transactional
    public TaskResponse createTask(TaskCreateRequest request, Jwt jwt, UUID xOrgID) {
        if (!userService.hasUserAccessToMembership(jwt, xOrgID, request.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the task's membership in this organization");
        }

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
     * Retrieves all tasks for a specific account (membership) ID if the user has access.
     *
     * @param jwt       The {@link Jwt} of the authenticated user.
     * @param accountId The UUID of the membership for which tasks are being requested.
     * @param xOrgID    The UUID of the organization.
     * @return A list of {@link TaskResponse} objects.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksForAccountId(Jwt jwt, UUID accountId, UUID xOrgID) {
        if (!userService.hasUserAccessToMembership(jwt, xOrgID, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to this membership in this organization");
        }

        return taskRepository.findAllByAccountIdOrderByIdAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves all tasks for all memberships (accounts) that the authenticated user has access to within an organization.
     *
     * @param jwt    The {@link Jwt} of the authenticated user.
     * @param xOrgID The UUID of the organization.
     * @return A list of {@link TaskAccountListResponse} objects.
     */
    @Transactional(readOnly = true)
    public List<TaskAccountListResponse> getTasksForAllAccounts(Jwt jwt, UUID xOrgID) {
        // Resolve the user's membership in the target organization
        var membership = userService.resolveMembership(jwt, xOrgID);

        // In this organization, we currently assume the user has access to their own account.
        // If we want to support multiple accounts or management views, we would fetch all relevant IDs.
        List<UUID> accountIds = List.of(membership.getId());

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
     * Updates an existing task within an organization.
     *
     * @param jwt     The {@link Jwt} of the authenticated user.
     * @param id      The UUID of the task to update.
     * @param request The {@link TaskUpdateRequest} containing updated details.
     * @param xOrgID  The UUID of the organization.
     * @return The updated {@link TaskResponse}.
     */
    @Transactional
    public TaskResponse updateTask(Jwt jwt, UUID id, TaskUpdateRequest request, UUID xOrgID) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!userService.hasUserAccessToMembership(jwt, xOrgID, task.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the task's membership in this organization");
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
     * Deletes the task with the specified ID within an organization.
     *
     * @param id     The UUID of the task to delete.
     * @param jwt    The {@link Jwt} of the authenticated user.
     * @param xOrgID The UUID of the organization.
     */
    @Transactional
    public void deleteTask(UUID id, Jwt jwt, UUID xOrgID) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!userService.hasUserAccessToMembership(jwt, xOrgID, task.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access to the task's membership in this organization");
        }

        taskRepository.delete(task);
    }

    /**
     * Validates a task creation request for required fields and field constraints.
     *
     * @param request The {@link TaskCreateRequest} to validate.
     * @throws ResponseStatusException if any validation check fails.
     */
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

    /**
     * Validates a task update request for required fields and field constraints.
     *
     * @param request The {@link TaskUpdateRequest} to validate.
     * @throws ResponseStatusException if any validation check fails.
     */
    private void validateUpdateRequest(TaskUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        validateCommonFields(request.getTitle(), request.getDuration(), request.getStatus(),
                request.getCognitiveLoad(), request.getCustomChunkSize());
    }

    /**
     * Validates fields common to both creation and update requests.
     *
     * @param title             The title of the task.
     * @param duration          The duration of the task.
     * @param status            The {@link TaskStatus} of the task.
     * @param cognitiveLoad     The {@link CognitiveLoad} of the task.
     * @param customChunkSize   An optional custom chunk size for scheduling.
     * @throws ResponseStatusException if any validation check fails.
     */
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

    /**
     * Checks if the specified dependency IDs are valid and applies them to the given task.
     * Ensures that a task doesn't depend on itself and that all dependencies belong to the same account.
     *
     * @param task               The {@link TaskEntity} to which dependencies are applied.
     * @param dependencyIds      A list of UUIDs of tasks that this task depends on.
     * @param accountId          The UUID of the membership that the task belongs to.
     * @param allowEmptyToClear  If true, an empty list of dependency IDs will clear existing dependencies.
     * @throws ResponseStatusException if any validation fails or a dependency task is not found.
     */
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dependency task not found for current user in this organization");
        }

        task.setDependencies(new LinkedHashSet<>(dependencies));
        assertNoDependencyCycle(task, accountId);
    }

    /**
     * Asserts that adding or updating a task's dependencies doesn't create a cyclic dependency.
     *
     * @param changedTask The task being created or updated.
     * @param accountId   The membership ID for which all tasks are fetched for cycle checking.
     * @throws ResponseStatusException if a cyclic dependency is detected.
     */
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

    /**
     * Determines if the dependency graph contains a cycle starting from the specified task.
     *
     * @param graph   A map representing the adjacency list of task dependencies.
     * @param startId The ID of the task to start checking from.
     * @return True if a cycle is detected, false otherwise.
     */
    private boolean containsCycle(Map<UUID, Set<UUID>> graph, UUID startId) {
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> visited = new HashSet<>();
        return dfsHasCycle(startId, graph, visiting, visited);
    }

    /**
     * Performs a depth-first search (DFS) to detect cycles in the dependency graph.
     *
     * @param current   The ID of the task currently being visited.
     * @param graph     A map representing the adjacency list of task dependencies.
     * @param visiting  A set of task IDs currently being visited in the current DFS path.
     * @param visited   A set of task IDs that have already been fully processed.
     * @return True if a cycle is detected, false otherwise.
     */
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

    /**
     * Converts a {@link TaskEntity} to a {@link TaskResponse}.
     *
     * @param entity The task entity to convert.
     * @return The resulting task response object.
     */
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

    /**
     * Converts an {@link OffsetDateTime} to an {@link Instant}.
     *
     * @param value The date-time to convert.
     * @return The resulting instant, or null if the input value was null.
     */
    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /**
     * Converts an {@link Instant} to an {@link OffsetDateTime} in UTC.
     *
     * @param value The instant to convert.
     * @return The resulting offset date-time, or null if the input value was null.
     */
    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
