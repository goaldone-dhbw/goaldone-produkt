package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Component for resolving the current user's account and organization based on the security context.
 * It provides methods to extract JWT information and resolve the corresponding database entities.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;

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
     * Resolves the {@link UserAccountEntity} for the current user based on the JWT in the security context.
     *
     * @return The {@link UserAccountEntity} associated with the current user.
     * @throws IllegalStateException if the account cannot be found for the JWT's subject claim.
     */
    public UserAccountEntity resolveCurrentAccount() {
        Jwt jwt = extractJwt();
        return resolveCurrentAccount(jwt);
    }

    /**
     * Resolves the {@link OrganizationEntity} for the current user based on their account.
     *
     * @return The {@link OrganizationEntity} the current user belongs to.
     * @throws IllegalStateException if the organization cannot be found for the user's account.
     */
    public OrganizationEntity resolveCurrentOrganization() {
        UserAccountEntity user = resolveCurrentAccount();
        return organizationRepository.findById(user.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException(
                "Organization not found for user " + user.getId()));
    }

    /**
     * Resolves the {@link UserAccountEntity} for a user based on a provided JWT.
     *
     * @param jwt The JWT to use for resolving the account.
     * @return The {@link UserAccountEntity} associated with the provided JWT.
     * @throws IllegalStateException if the account cannot be found for the JWT's identifier.
     */
    public UserAccountEntity resolveCurrentAccount(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");
        final String authUserId = (userId != null) ? userId : jwt.getSubject();
        return userAccountRepository.findByAuthUserId(authUserId)
            .orElseThrow(() -> new IllegalStateException(
                "Account not found for authUserId " + authUserId + " after JIT provisioning"));
    }
}
