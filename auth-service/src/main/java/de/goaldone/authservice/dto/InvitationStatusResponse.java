package de.goaldone.authservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for invitation status query response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvitationStatusResponse", description = "Response containing invitation status information")
public class InvitationStatusResponse {
    /**
     * The invitation token
     */
    @Schema(description = "Invitation token", example = "550e8400-e29b-41d4-a716-446655440004")
    private String token;

    /**
     * Invitation status: PENDING, ACCEPTED, DECLINED, or EXPIRED
     */
    @Schema(description = "Invitation status", example = "PENDING", allowableValues = {"PENDING", "ACCEPTED", "DECLINED", "EXPIRED", "NOT_FOUND"})
    private String status;

    /**
     * The invited email address
     */
    @Schema(description = "Email address that was invited", example = "newuser@example.com")
    private String invitedEmail;

    /**
     * Organization ID
     */
    @Schema(description = "Organization UUID", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID organizationId;

    /**
     * Organization name
     */
    @Schema(description = "Organization name", example = "Acme Corporation")
    private String organizationName;

    /**
     * Invitation expiration date
     */
    @Schema(description = "Timestamp when the invitation expires")
    private LocalDateTime expirationDate;

    /**
     * Email match information
     */
    @Schema(description = "Information about whether the email matches an existing user")
    private EmailMatch emailMatch;

    /**
     * Email match details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "EmailMatch", description = "Email matching result")
    public static class EmailMatch {
        /**
         * Whether an email match was found
         */
        @Schema(description = "Whether a user with this email already exists", example = "false")
        private boolean found;

        /**
         * User ID if match found
         */
        @Schema(description = "UUID of the user if a match was found", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID userId;
    }
}
