package de.goaldone.backend.exception;

/**
 * Exception thrown when schedule generation fails.
 */
public class ScheduleGenerationException extends RuntimeException {
    /**
     * @param message the error message
     */
    public ScheduleGenerationException(String message) {
        super(message);
    }

    /**
     * @param message the error message
     * @param cause the root cause
     */
    public ScheduleGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

