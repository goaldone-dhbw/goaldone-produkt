package de.goaldone.backend.exception;

/**
 * Exception thrown when an operation requires multiple linked accounts, but the account is either not linked
 * or is the only account in its identity.
 */
public class NotLinkedException extends RuntimeException {
    public NotLinkedException() {
        super("Account is not linked or is the only account in its identity");
    }
}
