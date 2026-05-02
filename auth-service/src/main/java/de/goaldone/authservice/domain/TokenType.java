package de.goaldone.authservice.domain;

/**
 * Types of verification tokens supported by the system.
 */
public enum TokenType {
    /**
     * Used for inviting new or existing users to an organization.
     */
    INVITATION,

    /**
     * Used for resetting a user's password.
     */
    PASSWORD_RESET
}
