package de.goaldone.backend.service;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
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

@ExtendWith(MockitoExtension.class)
class JitProvisioningServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private JitProvisioningService jitProvisioningService;

    @Test
    void provisionUser_userAlreadyExists_updatesLastSeenAt() {
        String sub = "existing-user";
        Jwt jwt = buildJwt(sub, List.of(Map.of("id", "org-1", "name", "Org 1")));

        UserAccountEntity existingUser = new UserAccountEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setAuthUserId(sub);
        existingUser.setOrganizationId(UUID.randomUUID());
        existingUser.setUserIdentityId(UUID.randomUUID());
        existingUser.setLastSeenAt(Instant.now().minusSeconds(1000));

        OrganizationEntity org = new OrganizationEntity();
        org.setId(existingUser.getOrganizationId());

        when(userAccountRepository.findAllByAuthUserId(sub)).thenReturn(List.of(existingUser));
        when(userIdentityRepository.findById(existingUser.getUserIdentityId())).thenReturn(Optional.of(new UserIdentityEntity()));
        when(organizationRepository.findByAuthCompanyId("org-1")).thenReturn(Optional.of(org));
        when(userAccountRepository.findByAuthUserIdAndOrganizationId(sub, org.getId())).thenReturn(Optional.of(existingUser));

        jitProvisioningService.provisionUser(jwt);

        // Verify identity was NOT created
        verify(userIdentityRepository, never()).save(any());
        
        // Verify account was updated with new lastSeenAt
        assertTrue(existingUser.getLastSeenAt().isAfter(Instant.now().minusSeconds(10)));
        verify(userAccountRepository).save(existingUser);
    }

    @Test
    void provisionUser_newUserOrgExists_createsIdentityAndAccount() {
        String sub = "new-user";
        String authCompanyId = "org-1";
        String orgName = "My Org";
        Jwt jwt = buildJwt(sub, List.of(Map.of("id", authCompanyId, "name", orgName)));

        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setAuthCompanyId(authCompanyId);
        org.setName(orgName);

        when(userAccountRepository.findAllByAuthUserId(sub)).thenReturn(Collections.emptyList());
        when(userIdentityRepository.save(any(UserIdentityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.findByAuthCompanyId(authCompanyId)).thenReturn(Optional.of(org));
        when(userAccountRepository.findByAuthUserIdAndOrganizationId(sub, orgId)).thenReturn(Optional.empty());

        jitProvisioningService.provisionUser(jwt);

        // Verify identity was created
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));

        // Verify account was created with correct org and identity
        verify(userAccountRepository).save(argThat(account ->
            account.getAuthUserId().equals(sub) &&
            account.getOrganizationId().equals(orgId)
        ));

        // Org should NOT be created/saved
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void provisionUser_newUserMultipleOrgs_provisionsAll() {
        String sub = "multi-user";
        Jwt jwt = buildJwt(sub, List.of(
            Map.of("id", "org-1", "name", "Org 1"),
            Map.of("id", "org-2", "name", "Org 2")
        ));

        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(UUID.randomUUID());
        org1.setAuthCompanyId("org-1");

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(UUID.randomUUID());
        org2.setAuthCompanyId("org-2");

        when(userAccountRepository.findAllByAuthUserId(sub)).thenReturn(Collections.emptyList());
        when(userIdentityRepository.save(any(UserIdentityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        when(organizationRepository.findByAuthCompanyId("org-1")).thenReturn(Optional.of(org1));
        when(organizationRepository.findByAuthCompanyId("org-2")).thenReturn(Optional.of(org2));
        
        when(userAccountRepository.findByAuthUserIdAndOrganizationId(sub, org1.getId())).thenReturn(Optional.empty());
        when(userAccountRepository.findByAuthUserIdAndOrganizationId(sub, org2.getId())).thenReturn(Optional.empty());

        jitProvisioningService.provisionUser(jwt);

        // Verify identity was created once
        verify(userIdentityRepository, times(1)).save(any(UserIdentityEntity.class));

        // Verify two accounts were created
        verify(userAccountRepository, times(2)).save(any(UserAccountEntity.class));
    }

    private Jwt buildJwt(String sub, List<Map<String, Object>> orgs) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .claim("user_id", sub)
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
