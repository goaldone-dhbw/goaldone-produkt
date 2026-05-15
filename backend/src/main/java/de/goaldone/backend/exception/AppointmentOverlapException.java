package de.goaldone.backend.exception;

/**
 * Exception thrown when a new or updated appointment overlaps with an existing one.
 */
public class AppointmentOverlapException extends RuntimeException {
    /**
     * @param message the error message describing the overlap
     */
    public AppointmentOverlapException(String message) {
        super(message);
    }
}

