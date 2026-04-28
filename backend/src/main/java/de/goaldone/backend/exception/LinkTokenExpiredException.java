package de.goaldone.backend.exception;

/**
 * Exception thrown when an account linking process fails because the link token has expired.
 */
public class LinkTokenExpiredException extends RuntimeException {
    /**
     * Constructs a new LinkTokenExpiredException for the given token.
     * @param token the expired UUID token
     */
    public LinkTokenExpiredException(java.util.UUID token) {
        super("Link token " + token + " has expired");
    }
}
