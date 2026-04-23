package de.goaldone.backend.exception;

public class WorkingTimeAccessDeniedException extends RuntimeException {
    public WorkingTimeAccessDeniedException(String message) {
        super(message);
    }
}

