package de.goaldone.backend.controller;

import de.goaldone.backend.api.TasksApi;
import de.goaldone.backend.model.Task;
import de.goaldone.backend.model.TaskCreate;
import de.goaldone.backend.model.TaskListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TaskController implements TasksApi {

    @Override
    public ResponseEntity<Task> createTask(UUID accountId, TaskCreate taskCreate) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Void> deleteTask(UUID accountId, UUID taskId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<Task> getTask(UUID accountId, UUID taskId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TaskListResponse> listTasks(UUID accountId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
