package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private CurrentUserResolver currentUserResolver;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void extractJwt_noAuthentication_throwsIllegalStateException() {
        SecurityContextHolder.getContext().setAuthentication(null);
        assertThrows(IllegalStateException.class, () -> currentUserResolver.extractJwt());
    }

    @Test
    void extractJwt_nonJwtAuthentication_throwsIllegalStateException() {
        // Set a non-JWT authentication (using a mock that is not JwtAuthenticationToken)
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "pass")
        );
        assertThrows(IllegalStateException.class, () -> currentUserResolver.extractJwt());
    }

    @Test
    void resolveCurrentAccount_accountNotFound_throwsIllegalStateException() {
        Jwt jwt = buildJwt("test-sub");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userAccountRepository.findByZitadelSub("test-sub")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentAccount());
    }

    @Test
    void resolveCurrentAccount_explicitJwt_accountNotFound_throwsIllegalStateException() {
        Jwt jwt = buildJwt("test-sub");
        when(userAccountRepository.findByZitadelSub("test-sub")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentAccount(jwt));
    }

    @Test
    void resolveCurrentOrganization_orgNotFound_throwsIllegalStateException() {
        Jwt jwt = buildJwt("test-sub");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserAccountEntity account = new UserAccountEntity();
        account.setId(UUID.randomUUID());
        account.setOrganizationId(UUID.randomUUID());

        when(userAccountRepository.findByZitadelSub("test-sub")).thenReturn(Optional.of(account));
        when(organizationRepository.findById(account.getOrganizationId())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentOrganization());
    }

    private Jwt buildJwt(String sub) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
