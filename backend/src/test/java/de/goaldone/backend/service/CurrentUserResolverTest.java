package de.goaldone.backend.service;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CurrentUserResolver.
 * Verifies JWT extraction and user/organization/membership resolution with the new entity model.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserResolverTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CurrentUserResolver currentUserResolver;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void extractJwt_noAuthentication_throwsIllegalStateException() {
        SecurityContextHolder.getContext().setAuthentication(null);
        assertThrows(IllegalStateException.class, () -> currentUserResolver.extractJwt());
    }

    @Test
    void extractJwt_nonJwtAuthentication_throwsIllegalStateException() {
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "pass")
        );
        assertThrows(IllegalStateException.class, () -> currentUserResolver.extractJwt());
    }

    @Test
    void resolveCurrentMembership_noOrgIdInContext_throwsIllegalStateException() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentMembership());
    }

    @Test
    void resolveCurrentMembership_membershipNotFound_throwsIllegalStateException() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set(orgId.toString());

        when(membershipRepository.findByUserIdAndOrganizationId(userId, orgId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentMembership());
    }

    @Test
    void resolveCurrentOrganization_orgNotFound_throwsIllegalStateException() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set(orgId.toString());

        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentOrganization());
    }

    @Test
    void resolveCurrentUser_userNotFound_throwsIllegalStateException() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> currentUserResolver.resolveCurrentUser());
    }

    private Jwt buildJwt(String userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId)
            .claim("user_id", userId)
            .claim("authorities", List.of("USER"))
            .claim("orgs", List.of(Map.of("id", "org-1", "name", "Org 1")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
