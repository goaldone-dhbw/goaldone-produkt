package de.goaldone.backend.controller;

import de.goaldone.backend.api.WorkingTimesApi;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.WorkingTimesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkingTimesController implements WorkingTimesApi {

    private final WorkingTimesService workingTimesService;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public ResponseEntity<WorkingTimeResponse> createWorkingTime(WorkingTimeCreateRequest workingTimeCreateRequest) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        WorkingTimeResponse response = workingTimesService.createWorkingTime(currentAccount.getId(), workingTimeCreateRequest);
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<WorkingTimeResponse> updateWorkingTime(UUID id, WorkingTimeUpdateRequest workingTimeUpdateRequest) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        WorkingTimeResponse response = workingTimesService.updateWorkingTime(currentAccount.getId(), id, workingTimeUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteWorkingTime(UUID id) {
        var currentAccount = currentUserResolver.resolveCurrentAccount();
        workingTimesService.deleteWorkingTime(currentAccount.getId(), id);
        return ResponseEntity.noContent().build();
    }
}