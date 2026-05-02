package de.goaldone.authservice.dto;

import lombok.*;

import java.util.Map;

/**
 * DTO for invitation acceptance request with support for both new account and linking flows.
 * Supports backward compatibility with simple requests (null acceptanceType defaults to smart detection).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationAcceptanceRequest {

    /**
     * Acceptance type: NEW_ACCOUNT, ACCOUNT_LINKING, or null for smart detection
     */
    private String acceptanceType;

    /**
     * Account linking context (required when acceptanceType == ACCOUNT_LINKING)
     */
    private AccountLinkingContext accountLinkingContext;

    /**
     * New password (required when acceptanceType == NEW_ACCOUNT)
     */
    private String newPassword;

    /**
     * Confirm password (required when acceptanceType == NEW_ACCOUNT)
     */
    private String confirmPassword;

    /**
     * Validates the request payload for consistency
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (acceptanceType != null) {
            if ("NEW_ACCOUNT".equals(acceptanceType)) {
                if (newPassword == null || newPassword.isBlank()) {
                    throw new IllegalArgumentException("newPassword is required for NEW_ACCOUNT acceptance type");
                }
                if (confirmPassword == null || confirmPassword.isBlank()) {
                    throw new IllegalArgumentException("confirmPassword is required for NEW_ACCOUNT acceptance type");
                }
                if (!newPassword.equals(confirmPassword)) {
                    throw new IllegalArgumentException("newPassword and confirmPassword must match");
                }
                if (accountLinkingContext != null) {
                    throw new IllegalArgumentException("accountLinkingContext must not be present for NEW_ACCOUNT acceptance type");
                }
            } else if ("ACCOUNT_LINKING".equals(acceptanceType)) {
                if (accountLinkingContext == null) {
                    throw new IllegalArgumentException("accountLinkingContext is required for ACCOUNT_LINKING acceptance type");
                }
                if (newPassword != null || confirmPassword != null) {
                    throw new IllegalArgumentException("newPassword and confirmPassword must not be present for ACCOUNT_LINKING acceptance type");
                }
            } else {
                throw new IllegalArgumentException("Invalid acceptanceType: " + acceptanceType + ". Must be NEW_ACCOUNT or ACCOUNT_LINKING");
            }
        }
    }

    /**
     * DTO for account linking context information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountLinkingContext {
        /**
         * ID of the user to link with
         */
        private Long userId;

        /**
         * Optional authentication proof (token, session id, etc.)
         */
        private String authenticationProof;

        /**
         * Additional metadata for linking (e.g., additional emails to verify)
         */
        private Map<String, String> metadata;
    }
}
