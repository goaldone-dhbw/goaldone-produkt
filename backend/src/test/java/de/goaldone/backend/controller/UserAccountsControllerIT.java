package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.model.AccountUpdateRequest;
import de.goaldone.backend.model.PasswordUpdateRequest;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080",
    "zitadel.goaldone.project-id=project-1",
    "zitadel.goaldone.org-id=root-org"
})
@ActiveProfiles("local")
class UserAccountsControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private ZitadelManagementClient zitadelManagementClient;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID orgId;
    private String zitadelOrgId = "zitadel-org-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();

        // Create org
        OrganizationEntity org = new OrganizationEntity();
        orgId = UUID.randomUUID();
        org.setId(orgId);
        org.setName("Test Organization");
        org.setZitadelOrgId(zitadelOrgId);
        org.setCreatedAt(Instant.now());
        organizationRepository.save(org);
    }

    // ============ DELETE /users/accounts/{accountId} Tests ============

    @Test
    void TC01_deleteAccount_validOwner_returns204() throws Exception {
        String sub = "owner-sub";
        UserAccountEntity account = seedAccount(sub);

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/users/accounts/{accountId}", account.getId())
                .with(jwtWithAuthorities(sub))
        )
            .andExpect(status().isNoContent());

        // Verify account deleted
        assertTrue(userAccountRepository.findById(account.getId()).isEmpty());
    }

    @Test
    void TC02_deleteAccount_noAccess_returns403() throws Exception {
        String ownerSub = "owner-sub";
        String differentSub = "different-sub";
        UserAccountEntity ownerAccount = seedAccount(ownerSub);
        // Create account for different user
        seedAccount(differentSub);

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/users/accounts/{accountId}", ownerAccount.getId())
                .with(jwtWithAuthorities(differentSub))
        )
            .andExpect(status().isForbidden());
    }

    @Test
    void TC03_deleteAccount_unauthenticated_returns401() throws Exception {
        String sub = "owner-sub";
        UserAccountEntity account = seedAccount(sub);

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/users/accounts/{accountId}", account.getId())
        )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void TC04_deleteAccount_accountNotFound_returns403() throws Exception {
        String sub = "owner-sub";
        seedAccount(sub);
        UUID randomAccountId = UUID.randomUUID();

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/users/accounts/{accountId}", randomAccountId)
                .with(jwtWithAuthorities(sub))
        )
            .andExpect(status().isForbidden());
    }

    // ============ PATCH /users/accounts/{accountId} Tests ============

    @Test
    void TC05_updateAccount_validOwner_returns200WithUpdatedData() throws Exception {
        String sub = "owner-sub";
        UserAccountEntity account = seedAccount(sub);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setFirstName("UpdatedFirstName");
        request.setLastName("UpdatedLastName");
        request.setEmail("updated@example.com");

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/users/accounts/{accountId}", account.getId())
                .with(jwtWithAuthorities(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId", notNullValue()))
            .andExpect(jsonPath("$.organizationId", notNullValue()));

        verify(zitadelManagementClient).updateUser(sub, request);
    }

    @Test
    void TC06_updateAccount_noAccess_returns403() throws Exception {
        String ownerSub = "owner-sub";
        String differentSub = "different-sub";
        UserAccountEntity ownerAccount = seedAccount(ownerSub);
        // Create account for different user
        seedAccount(differentSub);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setFirstName("UpdatedFirstName");

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/users/accounts/{accountId}", ownerAccount.getId())
                .with(jwtWithAuthorities(differentSub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden());
    }

    @Test
    void TC07_updateAccount_accountNotFound_returns403() throws Exception {
        String sub = "owner-sub";
        seedAccount(sub);
        UUID randomAccountId = UUID.randomUUID();

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setFirstName("UpdatedFirstName");

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/users/accounts/{accountId}", randomAccountId)
                .with(jwtWithAuthorities(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden());
    }

    @Test
    void TC08_updateAccount_unauthenticated_returns401() throws Exception {
        String sub = "owner-sub";
        UserAccountEntity account = seedAccount(sub);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setFirstName("UpdatedFirstName");

        mockMvc.perform(
            MockMvcRequestBuilders.patch("/users/accounts/{accountId}", account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized());
    }

    // ============ PUT /users/accounts/password Tests ============

    @Test
    void TC09_updatePassword_authenticatedUser_returns204() throws Exception {
        String sub = "owner-sub";
        seedAccount(sub);

        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setCurrentPassword("oldPassword123");
        request.setNewPassword("newPassword456");

        mockMvc.perform(
            MockMvcRequestBuilders.put("/users/accounts/password")
                .with(jwtWithAuthorities(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNoContent());

        verify(zitadelManagementClient).updateUserPassword(sub, "oldPassword123", "newPassword456");
    }

    @Test
    void TC10_updatePassword_accountNotFound_throwsInternalServerError() throws Exception {
        // When JWT sub is not in DB, CurrentUserResolver throws IllegalStateException.
        // This tests the edge case where JIT provisioning didn't run before the request.
        String unknownSub = "unknown-sub";

        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setCurrentPassword("oldPassword123");
        request.setNewPassword("newPassword456");

        // The exception will be thrown and cause a servlet error
        try {
            mockMvc.perform(
                MockMvcRequestBuilders.put("/users/accounts/password")
                    .with(jwtWithAuthorities(unknownSub))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            );
            // If we get here without exception, the request succeeded (shouldn't happen)
            fail("Expected IllegalStateException to be thrown");
        } catch (Exception e) {
            // Expected: IllegalStateException wrapped in a servlet exception
            assertTrue(e.getMessage().contains("Account not found") ||
                      e.getCause() instanceof IllegalStateException,
                      "Expected Account not found error, got: " + e.getMessage());
        }
    }

    @Test
    void TC11_updatePassword_unauthenticated_returns401() throws Exception {
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setCurrentPassword("oldPassword123");
        request.setNewPassword("newPassword456");

        mockMvc.perform(
            MockMvcRequestBuilders.put("/users/accounts/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized());
    }

    // ============ Helper Methods ============

    /**
     * Seeds DB with identity, account, and org. Stubs Zitadel responses for buildAccountListResponse.
     */
    private UserAccountEntity seedAccount(String sub) {
        // Create identity first (FK constraint)
        UserIdentityEntity identity = new UserIdentityEntity();
        UUID identityId = UUID.randomUUID();
        identity.setId(identityId);
        identity.setCreatedAt(Instant.now());
        userIdentityRepository.save(identity);

        // Create account
        UserAccountEntity account = new UserAccountEntity();
        UUID accountId = UUID.randomUUID();
        account.setId(accountId);
        account.setZitadelSub(sub);
        account.setUserIdentityId(identityId);
        account.setOrganizationId(orgId);
        account.setCreatedAt(Instant.now());
        userAccountRepository.save(account);

        // Stub Zitadel responses for buildAccountListResponse
        when(zitadelManagementClient.getUserGrantRoles(anyString(), anyString(), anyString()))
            .thenReturn(List.of("COMPANY_ADMIN"));
        when(zitadelManagementClient.getUser(anyString()))
            .thenReturn(Optional.empty());

        return account;
    }

    /**
     * Returns a RequestPostProcessor that attaches JWT with explicit authorities.
     * Required because custom JwtAuthenticationConverter is not auto-applied in tests.
     */
    private RequestPostProcessor jwtWithAuthorities(String sub) {
        return jwt()
            .jwt(j -> j.subject(sub))
            .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
