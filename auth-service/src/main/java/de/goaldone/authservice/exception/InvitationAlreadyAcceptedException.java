package de.goaldone.authservice.exception;

/**
 * Exception thrown when an invitation has already been accepted.
 */
public class InvitationAlreadyAcceptedException extends RuntimeException {
    public InvitationAlreadyAcceptedException(String message) {
        super(message);
    }

    public InvitationAlreadyAcceptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
