package de.goaldone.backend.exception;

public class LinkTokenExpiredException extends RuntimeException {
    public LinkTokenExpiredException(java.util.UUID token) {
        super("Link token " + token + " has expired");
    }
}
