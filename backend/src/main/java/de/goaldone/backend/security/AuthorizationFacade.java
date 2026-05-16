package de.goaldone.backend.security;

import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.service.CurrentUserResolver;
import de.goaldone.backend.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Request-scoped authorization facade that centralizes all access checks.
 * Caches the identity chain resolution per request to avoid redundant DB and Zitadel API calls.
 */
@Component
@RequestScope
@RequiredArgsConstructor
public class AuthorizationFacade {

    private final CurrentUserResolver currentUserResolver;
    private final UserIdentityService userIdentityService;

    private Jwt cachedJwt;
    private List<UUID> cachedAccountIds;
    private Boolean cachedSuperAdmin;

    /**
     * Requires the current user to be a SUPER_ADMIN.
     *
     * @throws ResponseStatusException with 403 if the user is not a super admin
     */
    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    /**
     * Requires the current user to have the specified role in the given organization.
     *
     * @param orgId the organization to check
     * @param role  the required role
     * @throws ResponseStatusException with 403 if the user does not have the role
     */
    public void requireOrgRole(UUID orgId, MemberRole role) {
        if (!userIdentityService.hasUserAccessToOrganizationWithRole(getJwt(), orgId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    /**
     * Requires the current user to be a member of the given organization (any role).
     *
     * @param orgId the organization to check
     * @throws ResponseStatusException with 403 if the user is not a member
     */
    public void requireOrgMembership(UUID orgId) {
        if (!userIdentityService.hasUserAccessToOrganization(getJwt(), orgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this organization");
        }
    }

    /**
     * Requires the current user to have access to the specified account via their identity chain.
     *
     * @param accountId the account to check access for
     * @throws ResponseStatusException with 403 if the user does not have access
     */
    public void requireAccountAccess(UUID accountId) {
        if (!getAccessibleAccountIds().contains(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No access to this account");
        }
    }

    /**
     * Returns all account IDs accessible to the current user (cached per request).
     *
     * @return list of account UUIDs
     */
    public List<UUID> getAccessibleAccountIds() {
        if (cachedAccountIds == null) {
            cachedAccountIds = userIdentityService.accountIdsForUser(getJwt());
        }
        return cachedAccountIds;
    }

    /**
     * Returns the current user's account entity resolved from the security context.
     *
     * @return the current user's account
     */
    public UserAccountEntity getCurrentAccount() {
        return currentUserResolver.resolveCurrentAccount();
    }

    private boolean isSuperAdmin() {
        if (cachedSuperAdmin == null) {
            cachedSuperAdmin = userIdentityService.isUserSuperAdmin(getJwt());
        }
        return cachedSuperAdmin;
    }

    private Jwt getJwt() {
        if (cachedJwt == null) {
            cachedJwt = currentUserResolver.extractJwt();
        }
        return cachedJwt;
    }
}
