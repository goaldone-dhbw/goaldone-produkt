package de.goaldone.backend.exception;

public class SameOrganizationLinkNotAllowedException extends RuntimeException {
    public SameOrganizationLinkNotAllowedException() {
        super("Cannot link accounts that belong to the same organization");
    }
}
