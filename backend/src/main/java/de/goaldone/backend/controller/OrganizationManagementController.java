package de.goaldone.backend.controller;

import de.goaldone.backend.api.OrgManagementApi;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.OrganizationListResponse;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.security.AuthorizationFacade;
import de.goaldone.backend.service.OrganizationManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing organization-related operations.
 * This controller handles the creation, listing, and deletion of organizations.
 */
@RestController
@RequiredArgsConstructor
public class OrganizationManagementController implements OrgManagementApi {

    private final OrganizationManagementService organizationManagementService;
    private final AuthorizationFacade authorizationFacade;

    /**
     * Creates a new organization along with its first organization administrator.
     * Access is restricted to users with the 'SUPER_ADMIN' role.
     *
     * @param createOrganizationRequest the request object containing organization and admin details
     * @return a {@link ResponseEntity} containing the created {@link OrganizationResponse} and HTTP status 201 (Created)
     */
    @Override
    public ResponseEntity<OrganizationResponse> createOrganization(CreateOrganizationRequest createOrganizationRequest) {
        authorizationFacade.requireSuperAdmin();
        OrganizationResponse response = organizationManagementService.createOrganization(createOrganizationRequest);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Lists all registered organizations with active and invited member counts.
     * Access is restricted to users with the 'SUPER_ADMIN' role.
     *
     * @return a {@link ResponseEntity} containing the {@link OrganizationListResponse} and HTTP status 200 (OK)
     */
    @Override
    public ResponseEntity<OrganizationListResponse> listOrganizations() {
        authorizationFacade.requireSuperAdmin();
        return ResponseEntity.ok(organizationManagementService.listOrganizations());
    }

    /**
     * Deletes an organization and all its members in a cascading operation.
     * Access is restricted to users with the 'SUPER_ADMIN' role.
     *
     * @param zitadelOrgId the UUID of the organization to delete
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @Override
    public ResponseEntity<Void> deleteOrganization(String zitadelOrgId) {
        authorizationFacade.requireSuperAdmin();
        organizationManagementService.deleteOrganization(zitadelOrgId);
        return ResponseEntity.noContent().build();
    }
}
