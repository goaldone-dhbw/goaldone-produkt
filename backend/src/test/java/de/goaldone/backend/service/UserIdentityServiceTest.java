package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.AccountListResponse;
import de.goaldone.backend.model.AccountResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ZitadelManagementClient zitadelManagementClient;

    @InjectMocks
    private UserIdentityService userIdentityService;

    @Test
    void findAccountsForIdentity_returnsAccountsForIdentity() {
        UUID identityId = UUID.randomUUID();
        UUID accountId1 = UUID.randomUUID();
        UUID accountId2 = UUID.randomUUID();

        UserAccountEntity account1 = new UserAccountEntity();
        account1.setId(accountId1);
        account1.setUserIdentityId(identityId);

        UserAccountEntity account2 = new UserAccountEntity();
        account2.setId(accountId2);
        account2.setUserIdentityId(identityId);

        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(account1, account2));

        List<UserAccountEntity> accounts = userIdentityService.findAccountsForIdentity(identityId);

        assertEquals(2, accounts.size());
        assertTrue(accounts.contains(account1));
        assertTrue(accounts.contains(account2));
    }

    @Test
    void buildAccountListResponse_accountNotFound_throwsIllegalStateException() {
        Jwt jwt = buildJwt("missing-sub");

        when(userAccountRepository.findByZitadelSub("missing-sub"))
            .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            userIdentityService.buildAccountListResponse(jwt)
        );
    }

    @Test
    void buildAccountListResponse_orgNotFound_throwsIllegalStateException() {
        String sub = "user-sub";
        UUID identityId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Jwt jwt = buildJwt(sub);

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub(sub);
        account.setUserIdentityId(identityId);
        account.setOrganizationId(orgId);

        when(userAccountRepository.findByZitadelSub(sub))
            .thenReturn(Optional.of(account));
        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(account));
        when(organizationRepository.findById(orgId))
            .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            userIdentityService.buildAccountListResponse(jwt)
        );
    }

    @Test
    void buildAccountListResponse_singleAccount_returnsResponseWithCorrectData() {
        String sub = "user-sub";
        UUID identityId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String orgName = "Test Organization";
        Jwt jwt = buildJwt(sub);

        UserAccountEntity account = new UserAccountEntity();
        account.setId(accountId);
        account.setZitadelSub(sub);
        account.setUserIdentityId(identityId);
        account.setOrganizationId(orgId);

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName(orgName);

        when(userAccountRepository.findByZitadelSub(sub))
            .thenReturn(Optional.of(account));
        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(account));
        when(organizationRepository.findById(orgId))
            .thenReturn(Optional.of(org));
        when(zitadelManagementClient.getUserGrantRoles(any(), any(), any()))
            .thenReturn(List.of("COMPANY_ADMIN"));

        AccountListResponse response = userIdentityService.buildAccountListResponse(jwt);

        assertNotNull(response);
        assertNotNull(response.getAccounts());
        assertEquals(1, response.getAccounts().size());

        AccountResponse accountResponse = response.getAccounts().get(0);
        assertEquals(accountId, accountResponse.getAccountId());
        assertEquals(orgId, accountResponse.getOrganizationId());
        assertEquals(orgName, accountResponse.getOrganizationName());
        assertEquals(List.of("COMPANY_ADMIN"), accountResponse.getRoles());
    }

    @Test
    void buildAccountListResponse_multipleAccountsDifferentOrgs_returnsAllMapped() {
        String sub = "user-sub";
        UUID identityId = UUID.randomUUID();
        UUID accountId1 = UUID.randomUUID();
        UUID accountId2 = UUID.randomUUID();
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();
        String orgName1 = "Org 1";
        String orgName2 = "Org 2";
        Jwt jwt = buildJwt(sub);

        UserAccountEntity account1 = new UserAccountEntity();
        account1.setId(accountId1);
        account1.setZitadelSub(sub);
        account1.setUserIdentityId(identityId);
        account1.setOrganizationId(orgId1);

        UserAccountEntity account2 = new UserAccountEntity();
        account2.setId(accountId2);
        account2.setZitadelSub("other-sub");
        account2.setUserIdentityId(identityId);
        account2.setOrganizationId(orgId2);

        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(orgId1);
        org1.setName(orgName1);

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(orgId2);
        org2.setName(orgName2);

        when(userAccountRepository.findByZitadelSub(sub))
            .thenReturn(Optional.of(account1));
        when(userAccountRepository.findAllByUserIdentityId(identityId))
            .thenReturn(List.of(account1, account2));
        when(organizationRepository.findById(orgId1))
            .thenReturn(Optional.of(org1));
        when(organizationRepository.findById(orgId2))
            .thenReturn(Optional.of(org2));
        when(zitadelManagementClient.getUserGrantRoles(any(), any(), any()))
            .thenReturn(List.of());

        AccountListResponse response = userIdentityService.buildAccountListResponse(jwt);

        assertNotNull(response);
        assertNotNull(response.getAccounts());
        assertEquals(2, response.getAccounts().size());

        // Verify account 1
        AccountResponse accountResponse1 = response.getAccounts().get(0);
        assertEquals(accountId1, accountResponse1.getAccountId());
        assertEquals(orgId1, accountResponse1.getOrganizationId());
        assertEquals(orgName1, accountResponse1.getOrganizationName());

        // Verify account 2
        AccountResponse accountResponse2 = response.getAccounts().get(1);
        assertEquals(accountId2, accountResponse2.getAccountId());
        assertEquals(orgId2, accountResponse2.getOrganizationId());
        assertEquals(orgName2, accountResponse2.getOrganizationName());
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
