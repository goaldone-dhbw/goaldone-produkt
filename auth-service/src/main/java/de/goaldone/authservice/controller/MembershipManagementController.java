package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing user memberships in organizations.
 * Handles membership creation, deletion, and role changes with business constraint validation.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/memberships")
@RequiredArgsConstructor
@Tag(name = "Memberships", description = "User membership management endpoints")
@Slf4j
public class MembershipManagementController {

    private final UserManagementService userManagementService;

    /**
     * DELETE /api/v1/users/{userId}/memberships/{companyId}
     * Delete a user's membership in an organization.
     * Business constraint: Cannot remove the last COMPANY_ADMIN from an organization.
     * Returns 409 Conflict if constraint violated.
     *
     * @param userId the user ID
     * @param companyId the organization (company) ID
     * @throws LastAdminViolationException if the user is the last admin in the organization
     */
    @DeleteMapping("/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove user from organization", description = "Deletes a user's membership in an organization. Cannot remove the last administrator.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Membership removed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User or organization not found"),
            @ApiResponse(responseCode = "409", description = "Cannot remove the last administrator from the organization")
    })
    public void deleteMembership(
            @PathVariable @Parameter(description = "User UUID") UUID userId,
            @PathVariable @Parameter(description = "Organization UUID") UUID companyId) {
        log.info("Attempting to delete membership for user {} in company {}", userId, companyId);
        userManagementService.deleteMembership(userId, companyId);
        log.info("Successfully deleted membership for user {} in company {}", userId, companyId);
    }

    @PatchMapping("/{companyId}")
    @Operation(summary = "Update user role in organization", description = "Changes a user's role in an organization. Cannot demote the last administrator.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Membership role updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User or organization not found"),
            @ApiResponse(responseCode = "409", description = "Cannot demote the last administrator")
    })
    public ResponseEntity<Void> updateMembershipRole(
            @PathVariable @Parameter(description = "User UUID") UUID userId,
            @PathVariable @Parameter(description = "Organization UUID") UUID companyId,
            @RequestParam @Parameter(description = "New role (COMPANY_ADMIN or USER)") Role newRole) {

        log.info("Attempting to change role for user {} in company {} to {}", userId, companyId, newRole);
        userManagementService.updateMembershipRole(userId, companyId, newRole);
        log.info("Successfully updated role for user {} in company {} to {}", userId, companyId, newRole);
        return ResponseEntity.ok().build();
    }
}
