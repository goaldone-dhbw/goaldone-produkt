package de.goaldone.backend.controller;

import de.goaldone.backend.api.SuperAdminManagementApi;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing Super Administrators.
 * This controller allows listing, inviting, and deleting super admins.
 * Access is restricted to users with the 'SUPER_ADMIN' role.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController implements SuperAdminManagementApi {

    private final SuperAdminService superAdminService;

    /**
     * Retrieves a list of all super administrators.
     *
     * @return a {@link ResponseEntity} containing a list of {@link SuperAdminResponse} objects
     */
    @Override
    public ResponseEntity<List<SuperAdminResponse>> listSuperAdmins() {
        return ResponseEntity.ok(superAdminService.listSuperAdmins());
    }

    /**
     * Invites a new user to become a super administrator.
     *
     * @param inviteSuperAdminRequest the request object containing the email of the user to invite
     * @return a {@link ResponseEntity} with HTTP status 201 (Created)
     */
    @Override
    public ResponseEntity<Void> inviteSuperAdmin(InviteSuperAdminRequest inviteSuperAdminRequest) {
        superAdminService.inviteSuperAdmin(inviteSuperAdminRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Deletes a super administrator by their user ID.
     *
     * @param userId the unique identifier of the user to remove from super admins
     * @return a {@link ResponseEntity} with HTTP status 204 (No Content)
     */
    @Override
    public ResponseEntity<Void> deleteSuperAdmin(String userId) {
        superAdminService.deleteSuperAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}
