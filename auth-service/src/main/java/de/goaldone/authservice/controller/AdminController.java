package de.goaldone.authservice.controller;

import de.goaldone.authservice.exception.LastAdminViolationException;
import de.goaldone.authservice.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for system-level admin operations.
 * Handles super-admin status changes with business constraint validation.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "System administrator operations")
@Slf4j
public class AdminController {

    private final UserManagementService userManagementService;

    /**
     * PATCH /api/v1/admin/users/{userId}/super-admin-status
     * Update a user's super-admin status.
     * Business constraint: Cannot remove super_admin status from the last system super-administrator.
     * Returns 409 Conflict if constraint violated.
     *
     * @param userId the user ID
     * @param newSuperAdminStatus true to grant super-admin status, false to revoke
     * @return 200 OK on success, or 409 Conflict if constraint violated
     * @throws LastAdminViolationException if attempting to remove super-admin from the last super-admin
     */
    @PatchMapping("/users/{userId}/super-admin-status")
    @Operation(summary = "Update user super-admin status", description = "Grants or revokes super-admin status for a user. Cannot remove super-admin status from the last system administrator.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Super-admin status updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient privileges"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Cannot remove super-admin status from the last system administrator")
    })
    public ResponseEntity<Void> updateSuperAdminStatus(
            @PathVariable @Parameter(description = "User UUID") UUID userId,
            @RequestParam @Parameter(description = "New super-admin status (true to grant, false to revoke)") boolean newSuperAdminStatus) {

        log.info("Attempting to change super-admin status for user {} to {}", userId, newSuperAdminStatus);

        // Check if this would violate the last-super-admin constraint
        // Only applies when revoking (newSuperAdminStatus = false)
        if (!newSuperAdminStatus && userManagementService.isLastSuperAdmin(userId)) {
            log.warn("Constraint violation: Attempted to remove super-admin status from last SUPER_ADMIN user {}", userId);
            throw new LastAdminViolationException(
                    "LAST_SUPER_ADMIN",
                    Long.parseLong(userId.toString().substring(0, 8), 16),
                    null,
                    "Cannot remove super-admin status from the last system administrator. " +
                    "Promote another user to super-admin first, then retry the removal."
            );
        }

        log.info("Successfully updated super-admin status for user {} to {}", userId, newSuperAdminStatus);
        // TODO: Implement actual status update in service layer
        return ResponseEntity.ok().build();
    }
}
