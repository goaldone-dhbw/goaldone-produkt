package de.goaldone.backend.security.expression;

import de.goaldone.backend.security.TenantContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Custom Spring Security expression root for per-organization RBAC checks.
 *
 * Provides the @PreAuthorize("hasOrgRole('ROLE_NAME')") DSL for checking
 * if the current user has a specific role in the organization context
 * stored in TenantContext (from the X-Org-ID header).
 */
@Slf4j
public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot
        implements MethodSecurityExpressionOperations {

    private Object filterObject;
    private Object returnObject;

    public CustomMethodSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }

    /**
     * Checks if the current user has the specified role in the active organization.
     *
     * The active organization is determined by the X-Org-ID header (stored in TenantContext).
     * The user's roles are extracted from the JWT 'orgs' claim.
     *
     * @param role the role name to check (e.g., "COMPANY_ADMIN")
     * @return true if the user has the role in the active organization, false otherwise
     */
    public boolean hasOrgRole(String role) {
        String activeOrgId = TenantContext.get();
        if (activeOrgId == null) {
            log.debug("X-Org-ID header not present; hasOrgRole returning false");
            return false;
        }

        Authentication auth = this.getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt)) {
            log.warn("Authentication missing or not a JWT");
            return false;
        }

        Jwt jwt = (Jwt) auth.getPrincipal();

        try {
            // Extract the 'orgs' claim which is an array of org memberships
            List<Map<String, Object>> orgs = jwt.getClaim("orgs");

            if (orgs == null || orgs.isEmpty()) {
                log.debug("User has no organization memberships (orgs claim is empty)");
                return false;
            }

            // Check if the user has the required role in the active organization
            return orgs.stream()
                    .anyMatch(
                            org ->
                                    activeOrgId.equals(org.get("id"))
                                            && role.equals(org.get("role")));

        } catch (Exception e) {
            log.error("Error checking organization role", e);
            return false;
        }
    }

    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    @Override
    public Object getFilterObject() {
        return filterObject;
    }

    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    @Override
    public Object getReturnObject() {
        return returnObject;
    }

    @Override
    public Object getThis() {
        return this;
    }
}
