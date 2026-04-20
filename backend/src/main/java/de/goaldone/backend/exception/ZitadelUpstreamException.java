package de.goaldone.backend.exception;

public class ZitadelUpstreamException extends RuntimeException {
    public ZitadelUpstreamException(String message) {
        super(message);
    }

    public ZitadelUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
