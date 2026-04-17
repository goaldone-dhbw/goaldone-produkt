package de.goaldone.backend.exception;

public class NotLinkedException extends RuntimeException {
    public NotLinkedException() {
        super("Account is not linked or is the only account in its identity");
    }
}
