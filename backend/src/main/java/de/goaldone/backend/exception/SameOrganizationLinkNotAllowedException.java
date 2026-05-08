package de.goaldone.backend.exception;

/**
 * Exception thrown when an attempt is made to link two accounts that belong to the same organization.
 * Linking is only allowed across different organizations.
 */
public class SameOrganizationLinkNotAllowedException extends RuntimeException {
    public SameOrganizationLinkNotAllowedException() {
        super("Cannot link accounts that belong to the same organization");
    }
}
