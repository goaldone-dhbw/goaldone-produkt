package de.goaldone.authservice.dto;

import lombok.*;

import java.util.UUID;

/**
 * DTO indicating whether an invitation can be accepted with account linking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationLinkingEligibility {
    /**
     * Whether the token is valid
     */
    private boolean tokenValid;

    /**
     * Whether account linking is possible
     */
    private boolean canLink;

    /**
     * Whether an email match was found
     */
    private boolean emailMatch;

    /**
     * The invited email address
     */
    private String invitedEmail;

    /**
     * The ID of the existing user if email match found
     */
    private UUID existingUserId;

    /**
     * Organization ID
     */
    private UUID organizationId;

    /**
     * Organization name
     */
    private String organizationName;
}
