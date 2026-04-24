package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TasksServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @InjectMocks
    private TasksService tasksService;

    private Jwt mockJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
    }

    @Test
    void createTask_validRequest_returnsCreatedTask() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setAccountId(UUID.randomUUID());
        request.setTitle("Dokumentation");
        request.setDuration(120);
        request.setStatus(TaskStatus.OPEN);
        request.setCognitiveLoad(CognitiveLoad.MODERATE);

        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tasksService.createTask(request);

        assertEquals("Dokumentation", response.getTitle());
        assertEquals(120, response.getDuration());
        assertEquals(TaskStatus.OPEN, response.getStatus());
        assertEquals(CognitiveLoad.MODERATE, response.getCognitiveLoad());
    }

    @Test
    void createTask_invalidDuration_throwsBadRequest() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setAccountId(UUID.randomUUID());
        request.setTitle("Some Task");
        request.setStatus(TaskStatus.OPEN);
        request.setCognitiveLoad(CognitiveLoad.LOW);
        request.setDuration(-10);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tasksService.createTask(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateTask_statusChangeToDone_updatesStatus() {
        UUID accountId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        TaskEntity existing = new TaskEntity();
        existing.setId(taskId);
        existing.setAccountId(accountId);
        existing.setTitle("Task");
        existing.setDuration(30);
        existing.setStatus(TaskStatus.OPEN);
        existing.setCognitiveLoad(CognitiveLoad.MODERATE);
        existing.setDependencies(new LinkedHashSet<>());

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("Task");
        request.setDuration(30);
        request.setStatus(TaskStatus.DONE);
        request.setCognitiveLoad(CognitiveLoad.MODERATE);
        request.setDependencyIds(null);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existing));
        when(userIdentityService.hasUserAccessToAccount(any(), eq(accountId))).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tasksService.updateTask(jwt, taskId, request);

        assertEquals(TaskStatus.DONE, response.getStatus());
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository, times(1)).save(captor.capture());
        assertEquals(TaskStatus.DONE, captor.getValue().getStatus());
    }

    @Test
    void updateTask_cyclicDependency_throwsBadRequest() {
        UUID accountId = UUID.randomUUID();
        UUID taskAId = UUID.randomUUID();
        UUID taskBId = UUID.randomUUID();
        Jwt jwt = mockJwt();

        TaskEntity taskA = new TaskEntity();
        taskA.setId(taskAId);
        taskA.setAccountId(accountId);
        taskA.setTitle("A");
        taskA.setDuration(10);
        taskA.setStatus(TaskStatus.OPEN);
        taskA.setCognitiveLoad(CognitiveLoad.LOW);
        taskA.setDependencies(new LinkedHashSet<>());

        TaskEntity taskB = new TaskEntity();
        taskB.setId(taskBId);
        taskB.setAccountId(accountId);
        taskB.setTitle("B");
        taskB.setDuration(10);
        taskB.setStatus(TaskStatus.OPEN);
        taskB.setCognitiveLoad(CognitiveLoad.LOW);
        taskB.setDependencies(new LinkedHashSet<>(List.of(taskA)));

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("A");
        request.setDuration(10);
        request.setStatus(TaskStatus.OPEN);
        request.setCognitiveLoad(CognitiveLoad.LOW);
        request.setDependencyIds(List.of(taskBId));

        when(taskRepository.findById(taskAId)).thenReturn(Optional.of(taskA));
        when(userIdentityService.hasUserAccessToAccount(any(), eq(accountId))).thenReturn(true);
        when(taskRepository.findAllByIdInAndAccountId(anyCollection(), eq(accountId))).thenReturn(List.of(taskB));
        when(taskRepository.findAllByAccountIdOrderByIdAsc(accountId)).thenReturn(List.of(taskA, taskB));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tasksService.updateTask(jwt, taskAId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
