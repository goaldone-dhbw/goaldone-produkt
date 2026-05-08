package de.goaldone.backend.exception;

/**
 * Exception thrown when an email is already in use in Zitadel.
 */
public class EmailAlreadyInUseException extends ConflictException {
    public EmailAlreadyInUseException(String email) {
        super("EMAIL_ALREADY_IN_USE: " + email);
    }
}
