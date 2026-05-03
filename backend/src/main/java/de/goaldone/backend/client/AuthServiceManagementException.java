package de.goaldone.backend.client;

/**
 * Exception representing an error that occurred during communication with the Auth-Service management API.
 */
public class AuthServiceManagementException extends RuntimeException {

    private final int statusCode;

    public AuthServiceManagementException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AuthServiceManagementException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
