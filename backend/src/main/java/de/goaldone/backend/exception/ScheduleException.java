package de.goaldone.backend.exception;

/**
 * Exception thrown when schedule generation fails.
 */
public class ScheduleException extends RuntimeException {

    /**
     * @param message the error message
     * @param cause the root cause
     */
    public ScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}

