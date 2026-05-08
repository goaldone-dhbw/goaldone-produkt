package de.goaldone.backend.exception;

/**
 * Exception representing an error that occurred during communication with the Zitadel IAM API.
 */
public class ZitadelApiException extends RuntimeException {

    /**
     * @param message the error message
     */
    public ZitadelApiException(String message) {
        super(message);
    }

    /**
     * @param message the error message
     * @param cause the underlying cause of the exception
     */
    public ZitadelApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
