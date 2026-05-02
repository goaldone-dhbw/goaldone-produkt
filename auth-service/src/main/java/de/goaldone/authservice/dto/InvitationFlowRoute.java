package de.goaldone.authservice.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * DTO indicating recommended invitation acceptance flow based on email matching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationFlowRoute {

    /**
     * The invitation token
     */
    private String token;

    /**
     * Email match information
     */
    private EmailMatch emailMatch;

    /**
     * Recommended flow type: NEW_ACCOUNT, ACCOUNT_LINKING, or FLEXIBLE
     */
    private String recommendedFlow;

    /**
     * Organization information
     */
    private OrganizationInfo organization;

    /**
     * Email match details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailMatch {
        /**
         * Whether an email match was found
         */
        private boolean found;

        /**
         * User ID if match found
         */
        private UUID userId;

        /**
         * The matched email
         */
        private String email;

        /**
         * User's full name if match found
         */
        private String userFullName;

        /**
         * Existing organizations the user is member of
         */
        private List<String> existingOrganizations;
    }

    /**
     * Organization information for this invitation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizationInfo {
        /**
         * Organization ID
         */
        private UUID id;

        /**
         * Organization name
         */
        private String name;

        /**
         * Default role that will be assigned
         */
        private String role;
    }
}
