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
        Jwt jwt = buildJwt(sub, "zitadel-org-1", "Org Name");

        UserAccountEntity existingUser = new UserAccountEntity();
        existingUser.setId(UUID.randomUUID());
        existingUser.setZitadelSub(sub);
        existingUser.setLastSeenAt(Instant.now().minusSeconds(1000));

        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.of(existingUser));

        jitProvisioningService.provisionUser(jwt);

        // Verify identity and org were NOT created
        verify(userIdentityRepository, never()).save(any());
        verify(organizationRepository, never()).save(any());

        // Verify account was updated with new lastSeenAt
        assertTrue(existingUser.getLastSeenAt().isAfter(Instant.now().minusSeconds(10)));
        verify(userAccountRepository).save(existingUser);
    }

    @Test
    void provisionUser_newUserOrgExists_createsIdentityAndAccount() {
        String sub = "new-user";
        String zitadelOrgId = "zitadel-org-1";
        String orgName = "My Org";
        Jwt jwt = buildJwt(sub, zitadelOrgId, orgName);

        UUID orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setZitadelOrgId(zitadelOrgId);
        org.setName(orgName);

        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.empty());
        when(organizationRepository.findByZitadelOrgId(zitadelOrgId)).thenReturn(Optional.of(org));

        jitProvisioningService.provisionUser(jwt);

        // Verify identity was created
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));

        // Verify account was created with correct org and identity
        verify(userAccountRepository).save(argThat(account ->
            account.getZitadelSub().equals(sub) &&
            account.getOrganizationId().equals(orgId)
        ));

        // Org should NOT be created/saved
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void provisionUser_newUserOrgNotFound_createsOrgIdentityAndAccount() {
        String sub = "new-user";
        String zitadelOrgId = "zitadel-org-new";
        String orgName = "New Org";
        Jwt jwt = buildJwt(sub, zitadelOrgId, orgName);

        UUID newOrgId = UUID.randomUUID();
        OrganizationEntity createdOrg = new OrganizationEntity();
        createdOrg.setId(newOrgId);
        createdOrg.setZitadelOrgId(zitadelOrgId);
        createdOrg.setName(orgName);

        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.empty());
        when(organizationRepository.findByZitadelOrgId(zitadelOrgId)).thenReturn(Optional.empty());
        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(createdOrg);

        jitProvisioningService.provisionUser(jwt);

        // Verify org was created
        verify(organizationRepository).save(argThat(org ->
            org.getZitadelOrgId().equals(zitadelOrgId) &&
            org.getName().equals(orgName)
        ));

        // Verify identity was created
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));

        // Verify account was created with new org
        verify(userAccountRepository).save(argThat(account ->
            account.getZitadelSub().equals(sub) &&
            account.getOrganizationId().equals(newOrgId)
        ));
    }

    @Test
    void provisionUser_orgCreationRaceCondition_retriesAndSucceeds() {
        String sub = "new-user";
        String zitadelOrgId = "zitadel-org-race";
        String orgName = "Race Org";
        Jwt jwt = buildJwt(sub, zitadelOrgId, orgName);

        UUID existingOrgId = UUID.randomUUID();
        OrganizationEntity existingOrg = new OrganizationEntity();
        existingOrg.setId(existingOrgId);
        existingOrg.setZitadelOrgId(zitadelOrgId);
        existingOrg.setName(orgName);

        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.empty());
        when(organizationRepository.findByZitadelOrgId(zitadelOrgId))
            .thenReturn(Optional.empty())  // First call: not found
            .thenReturn(Optional.of(existingOrg)); // Second call (in catch): found
        when(organizationRepository.save(any(OrganizationEntity.class)))
            .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        jitProvisioningService.provisionUser(jwt);

        // Verify org creation was attempted but failed
        verify(organizationRepository, times(1)).save(any(OrganizationEntity.class));

        // Verify org was fetched again in the catch block
        verify(organizationRepository, times(2)).findByZitadelOrgId(zitadelOrgId);

        // Verify identity and account were created with existing org
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));
        verify(userAccountRepository).save(argThat(account ->
            account.getOrganizationId().equals(existingOrgId)
        ));
    }

    @Test
    void provisionUser_orgCreationRaceConditionAndNotFound_throwsRuntimeException() {
        String sub = "new-user";
        String zitadelOrgId = "zitadel-org-lost";
        String orgName = "Lost Org";
        Jwt jwt = buildJwt(sub, zitadelOrgId, orgName);

        when(userAccountRepository.findByZitadelSub(sub)).thenReturn(Optional.empty());
        when(organizationRepository.findByZitadelOrgId(zitadelOrgId))
            .thenReturn(Optional.empty())  // First call: not found
            .thenReturn(Optional.empty()); // Second call (in catch): still not found
        when(organizationRepository.save(any(OrganizationEntity.class)))
            .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThrows(RuntimeException.class, () ->
            jitProvisioningService.provisionUser(jwt)
        );

        // Verify identity and account were NOT created
        verify(userIdentityRepository, never()).save(any());
        verify(userAccountRepository, never()).save(argThat(account -> account.getZitadelSub().equals(sub)));
    }

    private Jwt buildJwt(String sub, String zitadelOrgId, String orgName) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
            .claim("urn:zitadel:iam:user:resourceowner:name", orgName)
            .build();
        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
