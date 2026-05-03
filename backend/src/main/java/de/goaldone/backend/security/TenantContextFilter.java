package de.goaldone.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates the X-Org-ID header from incoming requests.
 *
 * This filter runs after authentication (after BearerTokenAuthenticationFilter)
 * and validates that the organization ID in the header matches one of the
 * organizations the user is a member of (from the JWT 'orgs' claim).
 *
 * If validation fails, returns 403 Forbidden.
 * If validation succeeds, stores the organization ID in TenantContext.
 *
 * The organization context is cleared in a finally block to prevent ThreadLocal leaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String xOrgId = request.getHeader("X-Org-ID");

            // Only validate if the header is present
            if (xOrgId != null && !xOrgId.isEmpty()) {
                // Validate the organization ID against the user's JWT memberships
                if (!isValidOrgId(xOrgId)) {
                    log.warn(
                            "Unauthorized access attempt: X-Org-ID {} not in user's memberships",
                            xOrgId);
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.getWriter()
                            .write(
                                    "{\"error\": \"User is not a member of the requested organization\"}");
                    return;
                }

                // Store in TenantContext for this request
                TenantContext.set(xOrgId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear the ThreadLocal to prevent leaks
            TenantContext.clear();
        }
    }

    /**
     * Validates that the requested organization ID is one of the user's memberships.
     *
     * Extracts the 'orgs' claim from the JWT and checks if the organization ID exists
     * in the list of memberships.
     *
     * @param requestedOrgId the organization ID from the X-Org-ID header
     * @return true if the user is a member of the organization, false otherwise
     */
    private boolean isValidOrgId(String requestedOrgId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt)) {
            log.warn("Authentication missing or not a JWT");
            return false;
        }

        Jwt jwt = (Jwt) auth.getPrincipal();

        try {
            // Extract the 'orgs' claim which is an array of org memberships
            List<Map<String, Object>> orgs = jwt.getClaim("orgs");

            if (orgs == null || orgs.isEmpty()) {
                log.warn("User has no organization memberships (orgs claim is empty)");
                return false;
            }

            // Check if the requested organization ID exists in the user's memberships
            return orgs.stream()
                    .anyMatch(org -> requestedOrgId.equals(org.get("id")));

        } catch (Exception e) {
            log.error("Error validating organization membership", e);
            return false;
        }
    }
}
