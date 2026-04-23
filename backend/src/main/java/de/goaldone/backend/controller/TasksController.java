package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.TasksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TasksController implements TasksApi {

    private final TasksService tasksService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<TaskResponse> createTask(TaskCreateRequest taskCreateRequest) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        TaskResponse createdTask = tasksService.createTask(currentAccount.getId(), taskCreateRequest);
        return ResponseEntity.status(201).body(createdTask);
    }

    @Override
    public ResponseEntity<Void> deleteTask(UUID id) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        tasksService.deleteTask(currentAccount.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<TaskResponse>> getTasks() {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        return ResponseEntity.ok(tasksService.getTasks(currentAccount.getId()));
    }

    @Override
    public ResponseEntity<TaskResponse> updateTask(UUID id, TaskUpdateRequest taskUpdateRequest) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        TaskResponse updatedTask = tasksService.updateTask(currentAccount.getId(), id, taskUpdateRequest);
        return ResponseEntity.ok(updatedTask);
    }
}
