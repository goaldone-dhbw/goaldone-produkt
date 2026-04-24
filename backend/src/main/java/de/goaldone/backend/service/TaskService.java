package de.goaldone.backend.service;

import de.goaldone.backend.model.TaskListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    // TODO: Implement task service methods

    /**
     * Get all tasks from db for a specific account
     * @param accountID Specified account
     * @return List of tasks
     */
    public TaskListResponse listTasks(UUID accountID) {
        return null; //TODO
    }

}
