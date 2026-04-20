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

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserAccountRepository userAccountRepository;
    private final OrganizationRepository organizationRepository;

    public Jwt extractJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("JWT authentication required");
        }
        return (Jwt) jwtAuth.getPrincipal();
    }

    public UserAccountEntity resolveCurrentAccount() {
        Jwt jwt = extractJwt();
        return userAccountRepository.findByZitadelSub(jwt.getSubject())
            .orElseThrow(() -> new IllegalStateException(
                "Account not found for sub " + jwt.getSubject() + " after JIT provisioning"));
    }

    public OrganizationEntity resolveCurrentOrganization() {
        UserAccountEntity user = resolveCurrentAccount();
        return organizationRepository.findById(user.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException(
                "Organization not found for user " + user.getId()));
    }

    public UserAccountEntity resolveCurrentAccount(Jwt jwt) {
        return userAccountRepository.findByZitadelSub(jwt.getSubject())
            .orElseThrow(() -> new IllegalStateException(
                "Account not found for sub " + jwt.getSubject() + " after JIT provisioning"));
    }
}
