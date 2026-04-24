package de.goaldone.backend.controller;

import de.goaldone.backend.api.OrganizationManagementApi;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.service.OrganizationManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrganizationManagementController implements OrganizationManagementApi {

    private final OrganizationManagementService organizationManagementService;

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<OrganizationResponse> createOrganization(CreateOrganizationRequest createOrganizationRequest) {
        OrganizationResponse response = organizationManagementService.createOrganization(createOrganizationRequest);
        return ResponseEntity.status(201).body(response);
    }
}
