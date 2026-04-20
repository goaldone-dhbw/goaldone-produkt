package de.goaldone.backend.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public static final String ERROR_CODE = "EMAIL_ALREADY_IN_USE";

    public EmailAlreadyInUseException(String message) {
        super(message);
    }
}
