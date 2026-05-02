package de.goaldone.authservice.exception;

/**
 * Exception thrown when an invitation has already been declined.
 */
public class InvitationAlreadyDeclinedException extends RuntimeException {
    public InvitationAlreadyDeclinedException(String message) {
        super(message);
    }

    public InvitationAlreadyDeclinedException(String message, Throwable cause) {
        super(message, cause);
    }
}
