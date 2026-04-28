package de.goaldone.backend.exception;

/**
 * Exception thrown when a new or updated working time range overlaps with an existing one.
 */
public class WorkingTimeOverlapException extends RuntimeException {
    /**
     * @param message the error message
     */
    public WorkingTimeOverlapException(String message) {
        super(message);
    }
}

