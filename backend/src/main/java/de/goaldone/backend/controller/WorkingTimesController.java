package de.goaldone.backend.controller;

import de.goaldone.backend.api.WorkingTimesApi;
import de.goaldone.backend.model.WorkingTimeCreateRequest;
import de.goaldone.backend.model.WorkingTimeListResponse;
import de.goaldone.backend.model.WorkingTimeResponse;
import de.goaldone.backend.model.WorkingTimeUpdateRequest;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.WorkingTimesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing working times.
 * Provides endpoints for retrieving, creating, updating, and deleting working time slots.
 */
@RestController
@RequiredArgsConstructor
public class WorkingTimesController implements WorkingTimesApi {

    private final WorkingTimesService workingTimesService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * Retrieves all working time slots for the current user in the context of the specified organization.
     *
     * @param xOrgID the organization ID context for the request
     * @return a {@link ResponseEntity} containing a {@link WorkingTimeListResponse}
     */
    @Override
    public ResponseEntity<WorkingTimeListResponse> getWorkingTimes(@RequestHeader("X-Org-ID") UUID xOrgID) {
        var jwt = currentUserResolver.extractJwt();
        WorkingTimeListResponse response = workingTimesService.getWorkingTimes(jwt, xOrgID);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new working time slot for the current user in the context of the specified organization.
     *
     * @param xOrgID the organization ID context for the request
     * @param workingTimeCreateRequest the request object containing the working time details
     * @return a {@link ResponseEntity} containing the created {@link WorkingTimeResponse} with HTTP status 201 (Created)
     */
    @Override
    public ResponseEntity<WorkingTimeResponse> createWorkingTime(@RequestHeader("X-Org-ID") UUID xOrgID, WorkingTimeCreateRequest workingTimeCreateRequest) {
        var jwt = currentUserResolver.extractJwt();
        WorkingTimeResponse response = workingTimesService.createWorkingTime(jwt, workingTimeCreateRequest, xOrgID);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Updates an existing working time slot.
     *
     * @param xOrgID the organization ID context for the request
     * @param id the unique identifier (UUID) of the working time slot to update
     * @param workingTimeUpdateRequest the request object containing the updated working time details
     * @return a {@link ResponseEntity} containing the updated {@link WorkingTimeResponse}
     */
    @Override
    public ResponseEntity<WorkingTimeResponse> updateWorkingTime(@RequestHeader("X-Org-ID") UUID xOrgID, UUID id, WorkingTimeUpdateRequest workingTimeUpdateRequest) {
        var jwt = currentUserResolver.extractJwt();
        WorkingTimeResponse response = workingTimesService.updateWorkingTime(jwt, id, workingTimeUpdateRequest, xOrgID);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a specific working time slot.
     *
     * @param xOrgID the organization ID context for the request
     * @param id the unique identifier (UUID) of the working time slot to delete
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @Override
    public ResponseEntity<Void> deleteWorkingTime(@RequestHeader("X-Org-ID") UUID xOrgID, UUID id) {
        var jwt = currentUserResolver.extractJwt();
        workingTimesService.deleteWorkingTime(jwt, id, xOrgID);
        return ResponseEntity.noContent().build();
    }
}