package de.goaldone.authservice.exception;

/**
 * Exception thrown when an invitation token is invalid.
 */
public class InvitationInvalidTokenException extends RuntimeException {
    public InvitationInvalidTokenException(String message) {
        super(message);
    }

    public InvitationInvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
