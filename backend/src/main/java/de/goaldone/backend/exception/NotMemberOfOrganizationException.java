package de.goaldone.backend.exception;

/**
 * Exception thrown when a user attempts to perform an operation in an organization they don't belong to.
 */
public class NotMemberOfOrganizationException extends RuntimeException {
    public NotMemberOfOrganizationException(String message) {
        super(message);
    }
}
