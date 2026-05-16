package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskAccountListResponse;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.TasksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing tasks.
 * This controller handles HTTP requests for creating, updating, deleting, and retrieving tasks.
 * It implements the generated {@link TasksApi} interface.
 */
@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TasksService tasksService;
    private final AuthorizationFacade authorizationFacade;

    /**
     * Creates a new task based on the provided request body.
     *
     * @param taskCreateRequest The request object containing task details.
     * @return A ResponseEntity containing the created {@link TaskResponse} and a 201 Created status.
     */
    @Override
    public ResponseEntity<TaskResponse> createTask(TaskCreateRequest taskCreateRequest) {
        authorizationFacade.requireAccountAccess(taskCreateRequest.getAccountId());
        TaskResponse createdTask = tasksService.createTask(taskCreateRequest);
        return ResponseEntity.status(201).body(createdTask);
    }

    /**
     * Deletes a task with the specified ID.
     *
     * @param id The UUID of the task to delete.
     * @return A ResponseEntity with a 204 No Content status upon successful deletion.
     */
    @Override
    public ResponseEntity<Void> deleteTask(UUID id) {
        tasksService.deleteTask(id, authorizationFacade.getAccessibleAccountIds());
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves all tasks associated with a specific account ID.
     *
     * @param accountId The UUID of the account for which to retrieve tasks.
     * @return A ResponseEntity containing a list of {@link TaskResponse} objects.
     */
    @Override
    public ResponseEntity<List<TaskResponse>> getTasksByAccount(UUID accountId) {
        authorizationFacade.requireAccountAccess(accountId);
        List<TaskResponse> tasks = tasksService.getTasksForAccountId(accountId);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Retrieves all tasks for all accounts that the current user has access to.
     *
     * @return A ResponseEntity containing a list of {@link TaskAccountListResponse} objects,
     *         each representing an account and its tasks.
     */
    @Override
    public ResponseEntity<List<TaskAccountListResponse>> getTasksForAllAccounts() {
        List<TaskAccountListResponse> tasks = tasksService.getTasksForAllAccounts(authorizationFacade.getAccessibleAccountIds());
        return ResponseEntity.ok(tasks);
    }

    /**
     * Updates an existing task with the specified ID using the provided request body.
     *
     * @param id The UUID of the task to update.
     * @param taskUpdateRequest The request object containing updated task details.
     * @return A ResponseEntity containing the updated {@link TaskResponse}.
     */
    @Override
    public ResponseEntity<TaskResponse> updateTask(UUID id, TaskUpdateRequest taskUpdateRequest) {
        TaskResponse updatedTask = tasksService.updateTask(id, taskUpdateRequest, authorizationFacade.getAccessibleAccountIds());
        return ResponseEntity.ok(updatedTask);
    }

    /**
     * Retrieves tasks filtered and sorted.
     *
     * @param status The {@link TaskStatus} to filter by.
     * @param cognitiveLoad The {@link CognitiveLoad} to filter by.
     * @param deadlineFrom The start of the deadline range (ISO 8601 string).
     * @param deadlineTo The end of the deadline range (ISO 8601 string).
     * @param minDuration The minimum estimated duration.
     * @param maxDuration The maximum estimated duration.
     * @param sortBy The property to sort by.
     * @param sortDirection The sort direction (asc or desc).
     * @param searchTerm The term to search for in title and description.
     * @return A ResponseEntity containing a list of matching {@link TaskResponse} objects.
     */
    @Override
    public ResponseEntity<List<TaskResponse>> getTasks(TaskStatus status, CognitiveLoad cognitiveLoad, String deadlineFrom, String deadlineTo, Integer minDuration, Integer maxDuration, String sortBy, String sortDirection, String searchTerm) {
        LocalDateTime from = deadlineFrom != null ? LocalDateTime.parse(deadlineFrom) : null;
        LocalDateTime to = deadlineTo != null ? LocalDateTime.parse(deadlineTo) : null;
        List<TaskResponse> tasks = tasksService.getTasks(authorizationFacade.getAccessibleAccountIds(), status, cognitiveLoad, from, to, minDuration, maxDuration, sortBy, sortDirection, searchTerm);
        return ResponseEntity.ok(tasks);
    }
}
