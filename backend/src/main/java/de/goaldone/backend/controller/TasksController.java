package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.model.TaskAccountListResponse;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.TasksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing tasks.
 * This controller handles HTTP requests for creating, updating, deleting, and retrieving tasks.
 * It implements the generated {@link TasksApi} interface and ensures that the organization context
 * and current user's identity are used for all operations.
 */
@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TasksService tasksService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * Creates a new task based on the provided request body within an organization.
     *
     * @param xOrgID            The organization ID context for the request.
     * @param taskCreateRequest The request object containing task details.
     * @return A ResponseEntity containing the created {@link TaskResponse} and a 201 Created status.
     */
    @Override
    public ResponseEntity<TaskResponse> createTask(UUID xOrgID, TaskCreateRequest taskCreateRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        TaskResponse createdTask = tasksService.createTask(taskCreateRequest, jwt, xOrgID);
        return ResponseEntity.status(201).body(createdTask);
    }

    /**
     * Deletes a task with the specified ID within an organization.
     *
     * @param xOrgID The organization ID context for the request.
     * @param taskId     The UUID of the task to delete.
     * @return A ResponseEntity with a 204 No Content status upon successful deletion.
     */
    @Override
    public ResponseEntity<Void> deleteTask(UUID xOrgID, UUID taskId) {
        Jwt jwt = currentUserResolver.extractJwt();
        tasksService.deleteTask(taskId, jwt, xOrgID);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves all tasks associated with a specific account (membership) ID within an organization.
     *
     * @param xOrgID    The organization ID context for the request.
     * @param accountId The UUID of the membership for which to retrieve tasks.
     * @return A ResponseEntity containing a list of {@link TaskResponse} objects.
     */
    @Override
    public ResponseEntity<List<TaskResponse>> getTasksByAccount(UUID xOrgID, UUID accountId) {
        Jwt jwt = currentUserResolver.extractJwt();
        List<TaskResponse> tasks = tasksService.getTasksForAccountId(jwt, accountId, xOrgID);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Retrieves all tasks for all memberships that the current user has access to within an organization.
     *
     * @param xOrgID The organization ID context for the request (optional).
     * @return A ResponseEntity containing a list of {@link TaskAccountListResponse} objects.
     */
    @Override
    public ResponseEntity<List<TaskAccountListResponse>> getTasksForAllAccounts(UUID xOrgID) {
        Jwt jwt = currentUserResolver.extractJwt();
        List<TaskAccountListResponse> tasks = tasksService.getTasksForAllAccounts(jwt, xOrgID);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Updates an existing task with the specified ID using the provided request body within an organization.
     *
     * @param xOrgID            The organization ID context for the request.
     * @param taskId                The UUID of the task to update.
     * @param taskUpdateRequest The request object containing updated task details.
     * @return A ResponseEntity containing the updated {@link TaskResponse}.
     */
    @Override
    public ResponseEntity<TaskResponse> updateTask(UUID xOrgID, UUID taskId, TaskUpdateRequest taskUpdateRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        TaskResponse updatedTask = tasksService.updateTask(jwt, taskId, taskUpdateRequest, xOrgID);
        return ResponseEntity.ok(updatedTask);
    }
}
