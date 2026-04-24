package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.exception.NotLinkedException;
import de.goaldone.backend.model.TaskAccountListResponse;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.TasksService;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TasksService tasksService;
    private final UserIdentityService userIdentityService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<TaskResponse> createTask(TaskCreateRequest taskCreateRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        if (!userIdentityService.hasUserAccessToAccount(jwt, taskCreateRequest.getAccountId())) {
            throw new NotLinkedException();
        }

        TaskResponse createdTask = tasksService.createTask(taskCreateRequest);
        return ResponseEntity.status(201).body(createdTask);
    }
    @Override
    public ResponseEntity<Void> deleteTask(UUID id) {
        Jwt jwt = currentUserResolver.extractJwt();
        tasksService.deleteTask(id, jwt);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<TaskResponse>> getTasksByAccount(UUID accountId) {
        Jwt jwt = currentUserResolver.extractJwt();
        List<TaskResponse> tasks = tasksService.getTasksForAccountId(jwt, accountId);
        return ResponseEntity.ok(tasks);
    }

    @Override
    public ResponseEntity<List<TaskAccountListResponse>> getTasksForAllAccounts() {
        Jwt jwt = currentUserResolver.extractJwt();
        List<TaskAccountListResponse> tasks = tasksService.getTasksForAllAccounts(jwt);
        return ResponseEntity.ok(tasks);
    }

    @Override
    public ResponseEntity<TaskResponse> updateTask(UUID id, TaskUpdateRequest taskUpdateRequest) {
        Jwt jwt = currentUserResolver.extractJwt();
        TaskResponse updatedTask = tasksService.updateTask(jwt, id, taskUpdateRequest);
        return ResponseEntity.ok(updatedTask);
    }
}
