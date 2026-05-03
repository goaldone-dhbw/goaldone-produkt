package de.goaldone.backend.security;

import lombok.experimental.UtilityClass;

/**
 * Thread-local storage for the current request's organization context.
 *
 * The organization ID (X-Org-ID header) is extracted and validated by
 * TenantContextFilter and stored here for access throughout the request
 * lifecycle. This decouples business logic from HTTP request handling.
 */
@UtilityClass
public class TenantContext {

    private static final ThreadLocal<String> orgIdHolder = new ThreadLocal<>();

    /**
     * Set the current organization ID for this request thread.
     *
     * @param orgId the organization ID (UUID as string)
     */
    public static void set(String orgId) {
        orgIdHolder.set(orgId);
    }

    /**
     * Get the current organization ID for this request thread.
     *
     * @return the organization ID, or null if not set
     */
    public static String get() {
        return orgIdHolder.get();
    }

    /**
     * Clear the organization ID for this request thread.
     * Must be called in a finally block to prevent ThreadLocal leaks.
     */
    public static void clear() {
        orgIdHolder.remove();
    }
}
