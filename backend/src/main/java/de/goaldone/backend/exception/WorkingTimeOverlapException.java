package de.goaldone.backend.exception;

public class WorkingTimeOverlapException extends RuntimeException {
    public WorkingTimeOverlapException(String message) {
        super(message);
    }
}

