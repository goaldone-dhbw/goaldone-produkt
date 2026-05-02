package de.goaldone.authservice.exception;

/**
 * Generic exception for invitation flow errors.
 */
public class InvitationFlowException extends RuntimeException {
    public InvitationFlowException(String message) {
        super(message);
    }

    public InvitationFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
