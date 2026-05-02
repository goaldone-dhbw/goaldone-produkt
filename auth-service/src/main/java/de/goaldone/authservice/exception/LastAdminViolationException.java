package de.goaldone.authservice.exception;

/**
 * Exception thrown when a business constraint protecting the last admin(s) is violated.
 * This occurs when attempting to:
 * - Remove the last administrator from an organization
 * - Demote the last administrator to a non-admin role
 * - Remove super_admin status from the last system super-administrator
 *
 * Returns RFC 7807 Problem Detail response with 409 Conflict status.
 */
public class LastAdminViolationException extends RuntimeException {

    private final String violationType; // LAST_ORG_ADMIN or LAST_SUPER_ADMIN
    private final Long affectedUserId;
    private final Long organizationId; // null for LAST_SUPER_ADMIN checks

    public LastAdminViolationException(
            String violationType,
            Long affectedUserId,
            Long organizationId,
            String message) {
        super(message);
        this.violationType = violationType;
        this.affectedUserId = affectedUserId;
        this.organizationId = organizationId;
    }

    public String getViolationType() {
        return violationType;
    }

    public Long getAffectedUserId() {
        return affectedUserId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }
}
