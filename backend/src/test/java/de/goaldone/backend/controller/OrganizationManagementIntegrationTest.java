package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.zitadel.model.*;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "zitadel.management-api-url=http://localhost:8099",
    "zitadel.service-account-token=test-token",
    "zitadel.goaldone.project-id=test-project-id",
    "zitadel.goaldone.org-id=test-main-org-id"
})
@ActiveProfiles("local")
class OrganizationManagementIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();
    private static final String GOALDONE_ORG_ID = "test-main-org-id";
    private static final String GOALDONE_PROJECT_ID = "test-project-id";

    @MockitoBean
    private ZitadelManagementClient zitadelManagementClient;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrganizationEntity goaldoneOrganization;

    @BeforeEach
    void setUp() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        // Delete in FK order: accounts → identities → organizations
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();

        // Create the Goaldone root organization
        goaldoneOrganization = organizationRepository.save(
            new OrganizationEntity(UUID.randomUUID(), GOALDONE_ORG_ID, "Goaldone", Instant.now())
        );

        // Pre-provision the super-admin user in the Goaldone root organization
        // This allows tests to verify access by mocking Zitadel grants
        UserIdentityEntity superAdminIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(superAdminIdentity);

        UserAccountEntity superAdminUser = new UserAccountEntity(
                UUID.randomUUID(), "super-admin-user",
                goaldoneOrganization.getId(), superAdminIdentity.getId(),
                Instant.now(), Instant.now(), new ArrayList<>());
        userAccountRepository.save(superAdminUser);
    }

    // TC1: Happy path
    @Test
    void testTC1_CreateOrganizationSuccess() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        when(zitadelManagementClient.emailExists(anyString())).thenReturn(false);
        when(zitadelManagementClient.addOrganization(anyString())).thenReturn("org-123");
        when(zitadelManagementClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("user-xyz");
        doNothing().when(zitadelManagementClient).addUserGrant(anyString(), anyString(), anyString(), anyString());
        doNothing().when(zitadelManagementClient).createInviteCode(anyString());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "admin@goaldone.de");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.zitadelOrganizationId").value("org-123"))
            .andExpect(jsonPath("$.name").value("GoalDone GmbH"))
            .andExpect(jsonPath("$.adminEmail").value("admin@goaldone.de"))
            .andExpect(jsonPath("$.createdAt").exists());

        // goaldone org (pre-provisioned) + endpoint org
        assertEquals(2, organizationRepository.count());
        assertTrue(organizationRepository.findByZitadelOrgId("org-123").isPresent());
    }

    // TC3: Email already exists
    @Test
    void testTC3_EmailAlreadyExists() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        when(zitadelManagementClient.emailExists(anyString())).thenReturn(true);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "existing@example.com");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE"));

        // Only goaldone org (pre-provisioned), endpoint created nothing
        assertEquals(1, organizationRepository.count());
    }

    // TC4: Organization name already exists
    @Test
    void testTC4_OrganizationNameAlreadyExists() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        organizationRepository.save(new de.goaldone.backend.entity.OrganizationEntity(
            java.util.UUID.randomUUID(), "org-existing", "GoalDone GmbH", Instant.now()
        ));

        when(zitadelManagementClient.emailExists(anyString())).thenReturn(false);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "admin@goaldone.de");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("ORGANIZATION_NAME_ALREADY_EXISTS"));
    }

    // TC8: Access without SUPER_ADMIN role
    @Test
    void testTC8_ForbiddenWithoutSuperAdminRole() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "admin@goaldone.de");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtForNonSuperAdmin("regular-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    // TC9: Invalid request data
    @Test
    void testTC9_InvalidRequestData() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        // Empty name
        Map<String, String> emptyName = new LinkedHashMap<>();
        emptyName.put("name", "");
        emptyName.put("adminEmail", "admin@goaldone.de");
        emptyName.put("adminFirstName", "Max");
        emptyName.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(emptyName)))
            .andExpect(status().isBadRequest());

        // Invalid email
        Map<String, String> missingField = new LinkedHashMap<>();
        missingField.put("name", "GoalDone GmbH");
        missingField.put("adminEmail", "invalid-email");
        missingField.put("adminFirstName", "Max");
        missingField.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(missingField)))
            .andExpect(status().isBadRequest());
    }

    // TC2: JIT provisioning on first login of invited admin
    @Test
    void testTC2_JitProvisioningOnFirstLoginOfInvitedAdmin() throws Exception {
        // Stub the caller (super-admin-user) as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        when(zitadelManagementClient.emailExists(anyString())).thenReturn(false);
        when(zitadelManagementClient.addOrganization(anyString())).thenReturn("org-jit-123");
        when(zitadelManagementClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("user-jit-xyz");
        doNothing().when(zitadelManagementClient).addUserGrant(anyString(), anyString(), anyString(), anyString());
        doNothing().when(zitadelManagementClient).createInviteCode(anyString());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "JIT Test Org");
        body.put("adminEmail", "admin-jit@example.com");
        body.put("adminFirstName", "Admin");
        body.put("adminLastName", "Jit");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/accounts")
            .with(jwt().jwt(buildJwtForInvitedUser("user-jit-xyz", "admin-jit@example.com", "org-jit-123"))))
            .andExpect(status().isOk());

        // goaldone org + endpoint org; invited user JIT reuses endpoint org (no new org)
        assertEquals(2, organizationRepository.count());
        // super-admin user (pre-provisioned) + invited user (JIT on second request)
        assertEquals(2, userAccountRepository.count());
        Optional<UserAccountEntity> account = userAccountRepository.findByZitadelSub("user-jit-xyz");
        assertTrue(account.isPresent());
        assertEquals("org-jit-123", organizationRepository.findById(account.get().getOrganizationId()).get().getZitadelOrgId());
    }

    // ===== GET /admins/organizations =====

    // New-TC1: List organizations with member counts — Zitadel is source of truth
    @Test
    void testListOrganizations_WithMemberCounts() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity orgA = new OrganizationEntity(UUID.randomUUID(), "zit-org-a", "Org Alpha", Instant.now());
        OrganizationEntity orgB = new OrganizationEntity(UUID.randomUUID(), "zit-org-b", "Org Beta", Instant.now());
        organizationRepository.save(orgA);
        organizationRepository.save(orgB);

        when(zitadelManagementClient.listOrganizations()).thenReturn(
            mockOrgsResponse(List.<String[]>of(
                new String[]{"test-main-org-id", "Home", "2024-01-01T00:00:00Z"},
                new String[]{"zit-org-a", "Org Alpha", "2024-01-01T00:00:00Z"},
                new String[]{"zit-org-b", "Org Beta", "2024-01-01T00:00:00Z"}
            ))
        );

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations").isArray())
                // Home org must not appear
                .andExpect(jsonPath("$.organizations[?(@.zitadelOrganizationId=='test-main-org-id')]").isEmpty())
                // Org Alpha: id populated from local DB
                .andExpect(jsonPath("$.organizations[?(@.name=='Org Alpha')].id",
                        org.hamcrest.Matchers.hasItem(orgA.getId().toString())))
                // Org Beta: id populated from local DB
                .andExpect(jsonPath("$.organizations[?(@.name=='Org Beta')].id",
                        org.hamcrest.Matchers.hasItem(orgB.getId().toString())));
    }

    // New-TC1b: Org exists in Zitadel but not in local DB — id must be null
    @Test
    void testListOrganizations_OrgWithoutLocalRecord_HasNullId() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        when(zitadelManagementClient.listOrganizations()).thenReturn(
            mockOrgsResponse(List.<String[]>of(
                new String[]{"zit-org-zitadel-only", "Zitadel Only Org", "2024-03-01T10:00:00Z"}
            ))
        );

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations[0].zitadelOrganizationId").value("zit-org-zitadel-only"))
                .andExpect(jsonPath("$.organizations[0].id").value(org.hamcrest.Matchers.nullValue()));
    }

    // New-TC2: List returns empty array when Zitadel only has the home org
    @Test
    void testListOrganizations_OnlyHomeOrgInZitadel_ReturnsEmpty() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        when(zitadelManagementClient.listOrganizations()).thenReturn(
            mockOrgsResponse(List.<String[]>of(
                new String[]{"test-main-org-id", "Home", "2024-01-01T00:00:00Z"}
            ))
        );

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations").isArray())
                .andExpect(jsonPath("$.organizations").isEmpty());
    }

    // ===== DELETE /admins/organizations/{orgId} =====

    // New-TC3: Delete organization with active and invited members
    @Test
    void testDeleteOrganization_WithActiveAndInvitedMembers() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-del", "Del Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity identity1 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity identity2 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "del-active-1", org.getId(), identity1.getId(), Instant.now(), null, new ArrayList<>()));
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "del-active-2", org.getId(), identity2.getId(), Instant.now(), null, new ArrayList<>()));

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-del")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-del", "Del Org", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-del")).thenReturn(
            mockUsersResponse(List.<String[]>of(
                new String[]{"del-active-1", "USER_STATE_ACTIVE"},
                new String[]{"del-active-2", "USER_STATE_ACTIVE"},
                new String[]{"del-invited-1", "USER_STATE_INITIAL"}
            ))
        );
        doNothing().when(zitadelManagementClient).deleteUser(anyString());
        doNothing().when(zitadelManagementClient).deleteOrganization(anyString());

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-del")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("del-active-1").isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("del-active-2").isPresent());
    }

    // New-TC4: Delete organization with no members
    @Test
    void testDeleteOrganization_NoMembers() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-empty", "Empty Org", Instant.now());
        organizationRepository.save(org);

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-empty")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-empty", "Empty Org", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-empty")).thenReturn(mockUsersResponse(List.<String[]>of()));
        doNothing().when(zitadelManagementClient).deleteOrganization(anyString());

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-empty")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC5: Delete cleans up identity when it is the last account
    @Test
    void testDeleteOrganization_CleansUpIdentityForLastAccount() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-identity-clean", "Identity Clean Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity identity = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "solo-user", org.getId(), identity.getId(), Instant.now(), null, new ArrayList<>()));

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-identity-clean")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-identity-clean", "Identity Clean Org", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-identity-clean")).thenReturn(
            mockUsersResponse(List.<String[]>of(new String[]{"solo-user", "USER_STATE_ACTIVE"}))
        );
        doNothing().when(zitadelManagementClient).deleteUser(anyString());
        doNothing().when(zitadelManagementClient).deleteOrganization(anyString());

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-identity-clean")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("solo-user").isPresent());
        assertFalse(userIdentityRepository.findById(identity.getId()).isPresent());
    }

    // New-TC6: Delete preserves identity when user has account in another org
    @Test
    void testDeleteOrganization_PreservesIdentityWithRemainingAccount() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity orgToDelete = new OrganizationEntity(UUID.randomUUID(), "zit-org-to-delete", "Org To Delete", Instant.now());
        OrganizationEntity otherOrg = new OrganizationEntity(UUID.randomUUID(), "zit-org-other", "Other Org", Instant.now());
        organizationRepository.save(orgToDelete);
        organizationRepository.save(otherOrg);

        UserIdentityEntity identity = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "linked-user", orgToDelete.getId(), identity.getId(), Instant.now(), null, new ArrayList<>()));
        // Second account in another org — identity must survive
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "linked-user-other-account", otherOrg.getId(), identity.getId(), Instant.now(), null, new ArrayList<>()));

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-to-delete")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-to-delete", "Org To Delete", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-to-delete")).thenReturn(
            mockUsersResponse(List.<String[]>of(new String[]{"linked-user", "USER_STATE_ACTIVE"}))
        );
        doNothing().when(zitadelManagementClient).deleteUser(anyString());
        doNothing().when(zitadelManagementClient).deleteOrganization(anyString());

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-to-delete")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isNoContent());

        assertFalse(userAccountRepository.findByZitadelSub("linked-user").isPresent());
        assertTrue(userIdentityRepository.findById(identity.getId()).isPresent());
        assertTrue(userAccountRepository.findByZitadelSub("linked-user-other-account").isPresent());
    }

    // New-TC7: Organization not found → 404
    @Test
    void testDeleteOrganization_NotFound() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        String randomId = UUID.randomUUID().toString();
        when(zitadelManagementClient.getOrganizationInfoById(anyString())).thenReturn(
            mockOrgsResponse(List.<String[]>of())
        );

        mockMvc.perform(delete("/admins/organizations/" + randomId)
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isNotFound());
    }

    // New-TC8: Partial failure — one user delete fails → 502
    @Test
    void testDeleteOrganization_PartialFailure() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-partial", "Partial Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity id1 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity id2 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity id3 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-1", org.getId(), id1.getId(), Instant.now(), null, new ArrayList<>()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-2", org.getId(), id2.getId(), Instant.now(), null, new ArrayList<>()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-3", org.getId(), id3.getId(), Instant.now(), null, new ArrayList<>()));

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-partial")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-partial", "Partial Org", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-partial")).thenReturn(
            mockUsersResponse(List.<String[]>of(
                new String[]{"partial-user-1", "USER_STATE_ACTIVE"},
                new String[]{"partial-user-2", "USER_STATE_ACTIVE"},
                new String[]{"partial-user-3", "USER_STATE_ACTIVE"}
            ))
        );
        doNothing().when(zitadelManagementClient).deleteUser("partial-user-1");
        doThrow(new ZitadelApiException("Zitadel delete failed", new RuntimeException()))
            .when(zitadelManagementClient).deleteUser("partial-user-2");
        doNothing().when(zitadelManagementClient).deleteUser("partial-user-3");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-partial")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isBadGateway());

        // Org must not be deleted — local record preserved for retry
        assertTrue(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC9: Zitadel org delete fails → 502, local record preserved
    @Test
    void testDeleteOrganization_ZitadelOrgDeleteFails() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-fail", "Fail Org", Instant.now());
        organizationRepository.save(org);

        when(zitadelManagementClient.getOrganizationInfoById("zit-org-fail")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"zit-org-fail", "Fail Org", "2024-01-01T00:00:00Z"}))
        );
        when(zitadelManagementClient.listUsersOfOrg("zit-org-fail")).thenReturn(mockUsersResponse(List.<String[]>of()));
        doThrow(new ZitadelApiException("Org delete failed", new RuntimeException()))
            .when(zitadelManagementClient).deleteOrganization("zit-org-fail");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-fail")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isBadGateway());

        // Local record must still exist so the admin can retry
        assertTrue(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC: Deleting home org is forbidden
    @Test
    void testDeleteOrganization_HomeOrgForbidden() throws Exception {
        // Stub the caller as SUPER_ADMIN
        stubSuperAdminRole("super-admin-user");

        // The goaldoneOrganization is already created in setUp with zitadelOrgId = "test-main-org-id"
        // Attempting to delete it should be forbidden

        when(zitadelManagementClient.getOrganizationInfoById("test-main-org-id")).thenReturn(
            mockOrgsResponse(List.<String[]>of(new String[]{"test-main-org-id", "Home", "2024-01-01T00:00:00Z"}))
        );

        mockMvc.perform(delete("/admins/organizations/" + "test-main-org-id")
                .with(jwt().jwt(buildJwt("super-admin-user"))))
                .andExpect(status().isForbidden());

        // Local record must still exist
        assertTrue(organizationRepository.findById(goaldoneOrganization.getId()).isPresent());
    }

    // New-TC10: Access as COMPANY_ADMIN → 403
    @Test
    void testDeleteOrganization_ForbiddenForCompanyAdmin() throws Exception {
        mockMvc.perform(delete("/admins/organizations/" + UUID.randomUUID())
                .with(jwt().jwt(buildJwtForNonSuperAdmin("company-admin-user"))))
                .andExpect(status().isForbidden());
    }

    // New-TC10b: Access GET as COMPANY_ADMIN → 403
    @Test
    void testListOrganizations_ForbiddenForCompanyAdmin() throws Exception {
        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwtForNonSuperAdmin("company-admin-user"))))
                .andExpect(status().isForbidden());
    }

    // New-TC11: No token → 401
    @Test
    void testDeleteOrganization_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(delete("/admins/organizations/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // New-TC11b: No token for GET → 401
    @Test
    void testListOrganizations_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/admins/organizations"))
                .andExpect(status().isUnauthorized());
    }

    // --- Mock builders ---

    /**
     * Builds an OrganizationServiceListOrganizationsResponse from a list of [id, name, creationDate] arrays.
     * Uses real POJO instances to avoid Mockito state machine conflicts when called inside thenReturn().
     */
    private OrganizationServiceListOrganizationsResponse mockOrgsResponse(List<String[]> orgs) {
        List<OrganizationServiceOrganization> list = new ArrayList<>();
        for (String[] o : orgs) {
            OrganizationServiceDetails details = new OrganizationServiceDetails();
            details.setCreationDate(OffsetDateTime.parse(o[2]));

            OrganizationServiceOrganization org = new OrganizationServiceOrganization();
            org.setId(o[0]);
            org.setName(o[1]);
            org.setDetails(details);
            list.add(org);
        }
        OrganizationServiceListOrganizationsResponse response = new OrganizationServiceListOrganizationsResponse();
        response.setResult(list);
        return response;
    }

    /**
     * Builds a UserServiceListUsersResponse from a list of [userId, state] arrays.
     * Uses real POJO instances to avoid Mockito state machine conflicts when called inside thenReturn().
     */
    private UserServiceListUsersResponse mockUsersResponse(List<String[]> users) {
        List<UserServiceUser> list = new ArrayList<>();
        for (String[] u : users) {
            UserServiceUser user = new UserServiceUser();
            user.setUserId(u[0]);
            user.setState(UserServiceUserState.valueOf(u[1]));
            list.add(user);
        }
        UserServiceListUsersResponse response = new UserServiceListUsersResponse();
        response.setResult(list);
        return response;
    }

    // --- JWT builders ---

    /**
     * Builds a JWT token for the given user subject.
     * The user is expected to be in the Goaldone root organization.
     */
    private Jwt buildJwt(String sub) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", sub + "@example.com")
            .claim("urn:zitadel:iam:user:resourceowner:id", GOALDONE_ORG_ID)
            .claim("urn:zitadel:iam:user:resourceowner:name", "Goaldone")
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    /**
     * Builds a JWT token for a user in a non-Goaldone organization.
     * Used for tests where SUPER_ADMIN access should be denied.
     */
    private Jwt buildJwtForNonSuperAdmin(String sub) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", sub + "@example.com")
            .claim("urn:zitadel:iam:user:resourceowner:id", "other-org-id")
            .claim("urn:zitadel:iam:user:resourceowner:name", "Other Org")
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    private Jwt buildJwtForInvitedUser(String sub, String email, String zitadelOrgId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", email)
            .claim("given_name", "Admin")
            .claim("family_name", "Jit")
            .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
            .claim("urn:zitadel:iam:user:resourceowner:name", "JIT Test Org")
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    /**
     * Stubs the given user as a SUPER_ADMIN by mocking the listGrantsForSpecificUser response.
     */
    private void stubSuperAdminRole(String userId) {
        AuthorizationServiceListAuthorizationsResponse response = mockAuthorizationsResponse("SUPER_ADMIN");
        when(zitadelManagementClient.listGrantsForSpecificUser(eq(GOALDONE_PROJECT_ID), eq(userId)))
            .thenReturn(response);
    }

    /**
     * Builds an AuthorizationServiceListAuthorizationsResponse with the given role keys.
     * Uses realistic mocks that avoid stubbing final methods.
     */
    private AuthorizationServiceListAuthorizationsResponse mockAuthorizationsResponse(String... roleKeys) {
        List<AuthorizationServiceAuthorization> authorizations = new ArrayList<>();

        if (roleKeys.length > 0) {
            AuthorizationServiceAuthorization auth = mock(AuthorizationServiceAuthorization.class);
            List<AuthorizationServiceRole> roles = new ArrayList<>();

            // Build list outside of the mock chain to avoid stubbing during stream consumption
            for (String key : roleKeys) {
                AuthorizationServiceRole role = mock(AuthorizationServiceRole.class);
                // Use doAnswer to handle final getKey() method
                when(role.getKey()).thenAnswer(invocation -> key);
                roles.add(role);
            }

            when(auth.getRoles()).thenReturn(roles);
            authorizations.add(auth);
        }

        AuthorizationServiceListAuthorizationsResponse response = mock(AuthorizationServiceListAuthorizationsResponse.class);
        when(response.getAuthorizations()).thenReturn(authorizations);
        return response;
    }
}
