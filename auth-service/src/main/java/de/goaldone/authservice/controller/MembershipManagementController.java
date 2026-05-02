package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.exception.LastAdminViolationException;
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

        // Check if this would violate the last-admin constraint
        if (userManagementService.isLastCompanyAdmin(userId, companyId)) {
            log.warn("Constraint violation: Attempted to remove last COMPANY_ADMIN user {} from company {}", userId, companyId);
            throw new LastAdminViolationException(
                    "LAST_ORG_ADMIN",
                    Long.parseLong(userId.toString().substring(0, 8), 16),
                    Long.parseLong(companyId.toString().substring(0, 8), 16),
                    "Cannot remove the last administrator from the organization. " +
                    "Promote another user to COMPANY_ADMIN first, then retry the removal."
            );
        }

        log.info("Successfully deleted membership for user {} in company {}", userId, companyId);
        // TODO: Implement actual membership deletion in service layer
    }

    /**
     * PATCH /api/v1/users/{userId}/memberships/{companyId}
     * Update a user's membership role in an organization.
     * Business constraints:
     * - Cannot demote the last COMPANY_ADMIN to a non-admin role (USER)
     * Returns 409 Conflict if constraint violated.
     *
     * @param userId the user ID
     * @param companyId the organization (company) ID
     * @param newRole the new role for the membership
     * @return 200 OK with updated membership, or 409 Conflict if constraint violated
     * @throws LastAdminViolationException if attempting to demote the last admin
     */
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

        // Check if this would violate the last-admin demotion constraint
        // Only applies when changing TO USER role from COMPANY_ADMIN
        if (newRole == Role.USER && userManagementService.isLastCompanyAdmin(userId, companyId)) {
            log.warn("Constraint violation: Attempted to demote last COMPANY_ADMIN user {} in company {} to USER", userId, companyId);
            throw new LastAdminViolationException(
                    "LAST_ORG_ADMIN",
                    Long.parseLong(userId.toString().substring(0, 8), 16),
                    Long.parseLong(companyId.toString().substring(0, 8), 16),
                    "Cannot demote the last administrator. " +
                    "Promote another user to COMPANY_ADMIN first, then retry the demotion."
            );
        }

        log.info("Successfully updated role for user {} in company {} to {}", userId, companyId, newRole);
        // TODO: Implement actual role update in service layer
        return ResponseEntity.ok().build();
    }
}
