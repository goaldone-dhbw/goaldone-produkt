package de.goaldone.backend.exception;

public class AlreadyLinkedException extends RuntimeException {
    public AlreadyLinkedException() {
        super("Accounts are already linked to the same identity");
    }
}
