package de.goaldone.backend.exception;

public class ZitadelApiException extends RuntimeException {

    public ZitadelApiException(String message) {
        super(message);
    }

    public ZitadelApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
