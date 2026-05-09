package de.goaldone.backend.service;

import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskResponse;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.model.TaskUpdateRequest;
import de.goaldone.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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

        Jwt jwt = mockJwt();
        when(userIdentityService.hasUserAccessToAccount(eq(jwt), eq(request.getAccountId()))).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = tasksService.createTask(request, jwt);

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

        Jwt jwt = mockJwt();
        when(userIdentityService.hasUserAccessToAccount(eq(jwt), eq(request.getAccountId()))).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> tasksService.createTask(request, jwt));

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
        existing.setTitle("Title");
        existing.setDuration(120);
        existing.setStatus(TaskStatus.IN_PROGRESS);
        existing.setCognitiveLoad(CognitiveLoad.MODERATE);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existing));
        when(userIdentityService.hasUserAccessToAccount(jwt, accountId)).thenReturn(true);
        when(taskRepository.save(any())).thenReturn(existing);

        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("Title");
        request.setDuration(120);
        request.setStatus(TaskStatus.DONE);
        request.setCognitiveLoad(CognitiveLoad.MODERATE);

        TaskResponse response = tasksService.updateTask(jwt, taskId, request);
        assertEquals(TaskStatus.DONE, response.getStatus());
    }

    @Test
    void getTasks_withFilters_returnsFilteredTasks() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = mockJwt();
        when(userIdentityService.accountIdsForUser(jwt)).thenReturn(List.of(accountId));

        TaskEntity entity = new TaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId(accountId);
        entity.setTitle("Task 1");
        entity.setDuration(60);
        entity.setStatus(TaskStatus.OPEN);
        entity.setCognitiveLoad(CognitiveLoad.HIGH);
        entity.setDeadline(Instant.parse("2026-06-01T10:00:00Z"));

        when(taskRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(entity));

        List<TaskResponse> results = tasksService.getTasks(
                jwt, TaskStatus.OPEN, CognitiveLoad.HIGH,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"), null,
                30, 100,
                "deadline", "desc"
        );

        assertEquals(1, results.size());
        assertEquals("Task 1", results.get(0).getTitle());
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
