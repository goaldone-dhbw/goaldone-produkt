package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JitProvisioningService.
 * Verifies that JIT provisioning logic works correctly with the new UserEntity and MembershipEntity models.
 * After PK unification, user.id == auth-service user UUID and organization.id == auth-service company UUID.
 */
@ExtendWith(MockitoExtension.class)
class JitProvisioningServiceTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private JitProvisioningService jitProvisioningService;

    /**
     * Test: Existing membership in an organization is updated with new lastSeenAt timestamp.
     */
    @Test
    void provisionUser_userAlreadyExists_updatesLastSeenAt() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString(), List.of(Map.of("id", orgId.toString(), "name", "Org 1")));

        UserEntity existingUser = new UserEntity();
        existingUser.setId(userId);
        existingUser.setCreatedAt(Instant.now().minusSeconds(1000));

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Org 1");
        org.setCreatedAt(Instant.now());

        MembershipEntity existingMembership = new MembershipEntity();
        existingMembership.setId(UUID.randomUUID());
        existingMembership.setOrganizationId(orgId);
        existingMembership.setUser(existingUser);
        existingMembership.setStatus("ACTIVE");
        existingMembership.setCreatedAt(Instant.now().minusSeconds(1000));
        existingMembership.setLastSeenAt(Instant.now().minusSeconds(1000));

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, orgId)).thenReturn(Optional.of(existingMembership));

        jitProvisioningService.provisionUser(jwt);

        // Verify membership was updated with new lastSeenAt
        assertTrue(existingMembership.getLastSeenAt().isAfter(Instant.now().minusSeconds(10)));
        verify(membershipRepository).save(existingMembership);
    }

    /**
     * Test: New user creates identity and membership in an existing organization.
     */
    @Test
    void provisionUser_newUserOrgExists_createsMembership() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String orgName = "My Org";
        Jwt jwt = buildJwt(userId.toString(), List.of(Map.of("id", orgId.toString(), "name", orgName)));

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName(orgName);
        org.setCreatedAt(Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, orgId)).thenReturn(Optional.empty());

        jitProvisioningService.provisionUser(jwt);

        // Verify user was created
        verify(userRepository).save(argThat(user ->
            user.getId().equals(userId)
        ));

        // Verify membership was created with correct org and user
        verify(membershipRepository).save(argThat(membership ->
            membership.getOrganizationId().equals(orgId) &&
            membership.getUser() != null &&
            membership.getStatus() == null  // Initial status not set in provisioning
        ));

        // Org should NOT be created/saved
        verify(organizationRepository, never()).save(any());
    }

    /**
     * Test: New user is provisioned in multiple organizations from JWT orgs claim.
     */
    @Test
    void provisionUser_newUserMultipleOrgs_provisionsAll() {
        UUID userId = UUID.randomUUID();
        UUID org1Id = UUID.randomUUID();
        UUID org2Id = UUID.randomUUID();
        Jwt jwt = buildJwt(userId.toString(), List.of(
            Map.of("id", org1Id.toString(), "name", "Org 1"),
            Map.of("id", org2Id.toString(), "name", "Org 2")
        ));

        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(org1Id);
        org1.setName("Org 1");
        org1.setCreatedAt(Instant.now());

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(org2Id);
        org2.setName("Org 2");
        org2.setCreatedAt(Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(organizationRepository.findById(org1Id)).thenReturn(Optional.of(org1));
        when(organizationRepository.findById(org2Id)).thenReturn(Optional.of(org2));

        when(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id)).thenReturn(Optional.empty());
        when(membershipRepository.findByUserIdAndOrganizationId(userId, org2Id)).thenReturn(Optional.empty());

        jitProvisioningService.provisionUser(jwt);

        // Verify user was created once
        verify(userRepository, times(1)).save(any(UserEntity.class));

        // Verify two memberships were created
        verify(membershipRepository, times(2)).save(any(MembershipEntity.class));
    }

    private Jwt buildJwt(String userId, List<Map<String, Object>> orgs) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(userId)
            .claim("user_id", userId)
            .claim("authorities", List.of("USER"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("orgs", orgs)
            .build();
        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
