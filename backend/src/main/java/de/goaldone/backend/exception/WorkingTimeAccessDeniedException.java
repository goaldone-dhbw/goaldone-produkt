package de.goaldone.backend.exception;

/**
 * Exception thrown when a user attempts to access or modify working times for an account they do not own
 * or have sufficient permissions for.
 */
public class WorkingTimeAccessDeniedException extends RuntimeException {
    /**
     * @param message the error message
     */
    public WorkingTimeAccessDeniedException(String message) {
        super(message);
    }
}

