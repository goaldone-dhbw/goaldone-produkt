package de.goaldone.backend.exception;

/**
 * Exception thrown when an email is already registered in the auth service.
 */
public class EmailAlreadyInUseException extends ConflictException {
    public EmailAlreadyInUseException(String email) {
        super("EMAIL_ALREADY_IN_USE: " + email);
    }
}
