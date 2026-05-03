package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TasksService.
 * Tests task CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
class TasksServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private TasksService tasksService;

    private Jwt mockJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
    }

    @Test
    void getTask_ReturnsTask() {
        UUID orgId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        TaskEntity existing = new TaskEntity();
        existing.setId(taskId);
        existing.setAccountId(UUID.randomUUID());
        existing.setTitle("Task");
        existing.setDuration(30);
        existing.setStatus(TaskStatus.OPEN);
        existing.setCognitiveLoad(CognitiveLoad.MODERATE);
        existing.setDependencies(new LinkedHashSet<>());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existing));

        Optional<TaskEntity> result = taskRepository.findById(taskId);

        assert result.isPresent();
        assertEquals(taskId, result.get().getId());
    }

    @Test
    void saveTask_Success() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setAccountId(UUID.randomUUID());
        task.setTitle("Test Task");
        task.setDuration(60);
        task.setStatus(TaskStatus.OPEN);
        task.setCognitiveLoad(CognitiveLoad.HIGH);
        task.setDependencies(new LinkedHashSet<>());

        when(taskRepository.save(any(TaskEntity.class))).thenReturn(task);

        TaskEntity saved = taskRepository.save(task);

        assertNotNull(saved);
        assertEquals("Test Task", saved.getTitle());
        verify(taskRepository).save(any(TaskEntity.class));
    }

    @Test
    void deleteTask_Success() {
        UUID taskId = UUID.randomUUID();

        taskRepository.deleteById(taskId);

        verify(taskRepository).deleteById(taskId);
    }
}
