package de.goaldone.backend.exception;

import java.util.List;

/**
 * Thrown when a cascading delete of an organization fails for some members.
 * The organization is not deleted in this case so the operation can be retried.
 */
public class PartialDeletionException extends RuntimeException {

    private final List<String> failedUserIds;

    /**
     * Creates a new PartialDeletionException.
     *
     * @param failedUserIds the Zitadel user IDs whose deletion failed
     */
    public PartialDeletionException(List<String> failedUserIds) {
        super("PARTIAL_DELETION_FAILURE");
        this.failedUserIds = List.copyOf(failedUserIds);
    }

    /**
     * Returns the list of Zitadel user IDs that could not be deleted.
     *
     * @return unmodifiable list of failed user IDs
     */
    public List<String> getFailedUserIds() {
        return failedUserIds;
    }
}
