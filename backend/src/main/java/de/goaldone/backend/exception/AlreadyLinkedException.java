package de.goaldone.backend.exception;

/**
 * Exception thrown when an attempt is made to link accounts that are already associated with the same identity.
 */
public class AlreadyLinkedException extends RuntimeException {
    public AlreadyLinkedException() {
        super("Accounts are already linked to the same identity");
    }
}
