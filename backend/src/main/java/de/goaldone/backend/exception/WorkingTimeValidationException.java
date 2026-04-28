package de.goaldone.backend.exception;

/**
 * Exception thrown when the validation of working time parameters fails (e.g., start time is after end time).
 */
public class WorkingTimeValidationException extends RuntimeException {
    /**
     * @param message the error message
     */
    public WorkingTimeValidationException(String message) {
        super(message);
    }
}

