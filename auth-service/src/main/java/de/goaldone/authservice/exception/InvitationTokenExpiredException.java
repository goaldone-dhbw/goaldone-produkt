package de.goaldone.authservice.exception;

/**
 * Exception thrown when an invitation token has expired.
 */
public class InvitationTokenExpiredException extends RuntimeException {
    public InvitationTokenExpiredException(String message) {
        super(message);
    }

    public InvitationTokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
