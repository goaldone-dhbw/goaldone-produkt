package de.goaldone.backend.controller;

import de.goaldone.backend.api.WorkingTimesApi;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeListResponse;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.WorkingTimesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkingTimesController implements WorkingTimesApi {

    private final WorkingTimesService workingTimesService;
    private final AuthorizationFacade authorizationFacade;

    @Override
    public ResponseEntity<WorkingTimeListResponse> getWorkingTimes() {
        WorkingTimeListResponse response = workingTimesService.getWorkingTimes();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<WorkingTimeResponse> createWorkingTime(WorkingTimeCreateRequest workingTimeCreateRequest) {
        authorizationFacade.requireAccountAccess(workingTimeCreateRequest.getAccountId());
        WorkingTimeResponse response = workingTimesService.createWorkingTime(workingTimeCreateRequest);
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<WorkingTimeResponse> updateWorkingTime(UUID id, WorkingTimeUpdateRequest workingTimeUpdateRequest) {
        WorkingTimeResponse response = workingTimesService.updateWorkingTime(id, workingTimeUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteWorkingTime(UUID id) {
        workingTimesService.deleteWorkingTime(id);
        return ResponseEntity.noContent().build();
    }
}
