package de.goaldone.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Carries account linking context through OIDC-PKCE flow.
 * Serializable for storage in Spring Authorization Server's authorization request parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLinkingContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The invitation token being accepted
     */
    private String invitationToken;

    /**
     * The email address being invited
     */
    private String invitedEmail;

    /**
     * The target organization ID
     */
    private UUID targetOrganizationId;

    /**
     * The role to be assigned (COMPANY_ADMIN or USER)
     */
    private String targetRole;

    /**
     * The ID of the existing user (for same-email scenario)
     * Empty if this is a new-email scenario
     */
    private UUID existingUserId;

    /**
     * When this linking context was created
     */
    private LocalDateTime linkingTimestamp;

    /**
     * Validates the context has all required fields
     *
     * @return true if all required fields are present
     */
    public boolean isValid() {
        return invitationToken != null && !invitationToken.isEmpty()
                && invitedEmail != null && !invitedEmail.isEmpty()
                && targetOrganizationId != null
                && targetRole != null && !targetRole.isEmpty()
                && linkingTimestamp != null;
    }

    /**
     * Validates consistency of the context for same-email scenario
     *
     * @return true if existing user ID is present
     */
    public boolean isSameEmailScenario() {
        return existingUserId != null;
    }
}
