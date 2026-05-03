package de.goaldone.backend.service;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Component for resolving the current user's membership and organization based on the security context and request headers.
 * It provides methods to extract JWT information and resolve the corresponding database entities.
 *
 * Supports two modes of operation:
 * - Header-based (X-Org-ID provided): Resolves membership in a specific organization.
 * - All-orgs mode (X-Org-ID missing): Resolves all memberships for the current user.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final HttpServletRequest request;

    /**
     * Extracts the JWT from the current security context.
     *
     * @return The {@link Jwt} from the authentication token.
     * @throws IllegalStateException if the current authentication is not a JWT token.
     */
    public Jwt extractJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("JWT authentication required");
        }
        return (Jwt) jwtAuth.getPrincipal();
    }

    /**
     * Resolves the current {@link MembershipEntity} using the 'X-Org-ID' header from TenantContext.
     * This is the primary way to determine the current organization context for mutations and member management.
     *
     * @return The {@link MembershipEntity} for the current user and organization.
     * @throws IllegalStateException if the X-Org-ID is missing or invalid, or if the membership is not found.
     */
    public MembershipEntity resolveCurrentMembership() {
        String orgIdStr = TenantContext.get();
        if (orgIdStr == null || orgIdStr.isBlank()) {
            throw new IllegalStateException("X-Org-ID header is missing or TenantContext not set");
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(orgIdStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid X-Org-ID: " + orgIdStr);
        }

        Jwt jwt = extractJwt();
        UUID userId = UUID.fromString(extractAuthUserId(jwt));

        return membershipRepository.findByUserIdAndOrganizationId(userId, orgId)
            .orElseThrow(() -> new IllegalStateException(
                "Membership not found for userId " + userId + " and organizationId " + orgId));
    }

    /**
     * Resolves the active {@link OrganizationEntity} using the X-Org-ID header from TenantContext.
     *
     * @return The {@link OrganizationEntity} for the current organization context.
     * @throws IllegalStateException if the X-Org-ID is missing or invalid, or if the organization is not found.
     */
    public OrganizationEntity resolveCurrentOrganization() {
        String orgIdStr = TenantContext.get();
        if (orgIdStr == null || orgIdStr.isBlank()) {
            throw new IllegalStateException("X-Org-ID header is missing or TenantContext not set");
        }

        UUID orgId;
        try {
            orgId = UUID.fromString(orgIdStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid X-Org-ID: " + orgIdStr);
        }

        return organizationRepository.findById(orgId)
            .orElseThrow(() -> new IllegalStateException("Organization not found for orgId " + orgId));
    }

    /**
     * Resolves all {@link MembershipEntity} objects for the current user.
     * This is used when X-Org-ID is not provided (for list endpoints in dual-mode).
     *
     * @return A list of all memberships for the current user.
     * @throws IllegalStateException if the user cannot be found.
     */
    public List<MembershipEntity> resolveAllMemberships() {
        Jwt jwt = extractJwt();
        UUID userId = UUID.fromString(extractAuthUserId(jwt));

        return membershipRepository.findAllByUserId(userId);
    }

    /**
     * Resolves the {@link UserEntity} for the current user based on the JWT in the security context.
     *
     * @return The {@link UserEntity} associated with the current user.
     * @throws IllegalStateException if the user cannot be found for the JWT's subject claim.
     */
    public UserEntity resolveCurrentUser() {
        Jwt jwt = extractJwt();
        UUID userId = UUID.fromString(extractAuthUserId(jwt));

        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException(
                "User not found for userId " + userId));
    }

    /**
     * Returns true if the X-Org-ID header is present in the current request.
     *
     * @return true if TenantContext has an organization ID set, false otherwise.
     */
    public boolean hasOrgIdContext() {
        return TenantContext.get() != null && !TenantContext.get().isBlank();
    }

    /**
     * Extracts the authentication user ID from the JWT, falling back to the subject if user_id claim is not present.
     *
     * @param jwt The JWT token.
     * @return The auth user ID.
     */
    private String extractAuthUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");
        return (userId != null) ? userId : jwt.getSubject();
    }
}
