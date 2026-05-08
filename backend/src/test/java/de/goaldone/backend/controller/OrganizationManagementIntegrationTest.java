package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import wiremock.com.google.common.net.HttpHeaders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        // Pre-provision the super-admin user so JIT only does a last_seen_at update
        // (not a full create) on every test request, keeping counts deterministic.
        UserIdentityEntity superAdminIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(superAdminIdentity);

        OrganizationEntity superAdminOrg = new OrganizationEntity(
                UUID.randomUUID(), "org-admin", "Admin Org", Instant.now());
        organizationRepository.save(superAdminOrg);

        UserAccountEntity superAdminUser = new UserAccountEntity(
                UUID.randomUUID(), "super-admin-user",
                superAdminOrg.getId(), superAdminIdentity.getId(),
                Instant.now(), Instant.now(), new ArrayList<>());
        userAccountRepository.save(superAdminUser);
    }

    // TC1: Happy path
    @Test
    void testTC1_CreateOrganizationSuccess() throws Exception {
        stubEmailNotExists();
        stubAddOrganization("org-123");
        stubAddHumanUser("user-xyz");
        stubAddUserGrant();
        stubCreateInviteCode();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "admin@goaldone.de");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.zitadelOrganizationId").value("org-123"))
            .andExpect(jsonPath("$.name").value("GoalDone GmbH"))
            .andExpect(jsonPath("$.adminEmail").value("admin@goaldone.de"))
            .andExpect(jsonPath("$.createdAt").exists());

        // super-admin org (pre-provisioned) + endpoint org
        assertEquals(2, organizationRepository.count());
        assertTrue(organizationRepository.findByZitadelOrgId("org-123").isPresent());
    }

    // TC3: Email already exists
    @Test
    void testTC3_EmailAlreadyExists() throws Exception {
        stubEmailExists();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "existing@example.com");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE"));

        // Only super-admin org (pre-provisioned), endpoint created nothing
        assertEquals(1, organizationRepository.count());
    }

    // TC4: Organization name already exists
    @Test
    void testTC4_OrganizationNameAlreadyExists() throws Exception {
        organizationRepository.save(new de.goaldone.backend.entity.OrganizationEntity(
            java.util.UUID.randomUUID(), "org-existing", "GoalDone GmbH", Instant.now()
        ));

        stubEmailNotExists();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "GoalDone GmbH");
        body.put("adminEmail", "admin@goaldone.de");
        body.put("adminFirstName", "Max");
        body.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
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
            .with(jwt().jwt(buildJwt("regular-user", "USER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    // TC9: Invalid request data
    @Test
    void testTC9_InvalidRequestData() throws Exception {
        // Empty name
        Map<String, String> emptyName = new LinkedHashMap<>();
        emptyName.put("name", "");
        emptyName.put("adminEmail", "admin@goaldone.de");
        emptyName.put("adminFirstName", "Max");
        emptyName.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(emptyName)))
            .andExpect(status().isBadRequest());

        // Missing required field
        Map<String, String> missingField = new LinkedHashMap<>();
        missingField.put("name", "GoalDone GmbH");
        missingField.put("adminEmail", "invalid-email");
        missingField.put("adminFirstName", "Max");
        missingField.put("adminLastName", "Mustermann");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(missingField)))
            .andExpect(status().isBadRequest());
    }

    // TC2: JIT provisioning on first login of invited admin
    @Test
    void testTC2_JitProvisioningOnFirstLoginOfInvitedAdmin() throws Exception {
        stubEmailNotExists();
        stubAddOrganization("org-jit-123");
        stubAddHumanUser("user-jit-xyz");
        stubAddUserGrant();
        stubCreateInviteCode();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", "JIT Test Org");
        body.put("adminEmail", "admin-jit@example.com");
        body.put("adminFirstName", "Admin");
        body.put("adminLastName", "Jit");

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/accounts")
            .with(jwt().jwt(buildJwtForInvitedUser("user-jit-xyz", "admin-jit@example.com", "org-jit-123"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
            .andExpect(status().isOk());

        // super-admin org + endpoint org; invited user JIT reuses endpoint org (no new org)
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
        OrganizationEntity orgA = new OrganizationEntity(UUID.randomUUID(), "zit-org-a", "Org Alpha", Instant.now());
        OrganizationEntity orgB = new OrganizationEntity(UUID.randomUUID(), "zit-org-b", "Org Beta", Instant.now());
        organizationRepository.save(orgA);
        organizationRepository.save(orgB);

        // Zitadel returns 3 orgs: home org (filtered), org-a, org-b
        // "test-main-org-id" is the configured home org — it must NOT appear in the response
        stubListAllOrganizations(
                "[{\"id\":\"test-main-org-id\",\"name\":\"Home\",\"details\":{\"creationDate\":\"2024-01-01T00:00:00Z\"}}" +
                ",{\"id\":\"zit-org-a\",\"name\":\"Org Alpha\",\"details\":{\"creationDate\":\"2024-01-01T00:00:00Z\"}}" +
                ",{\"id\":\"zit-org-b\",\"name\":\"Org Beta\",\"details\":{\"creationDate\":\"2024-01-01T00:00:00Z\"}}]");

        // Org A: 3 users (2 active, 1 initial)
        stubGrantsForOrg("zit-org-a", "[{\"userId\":\"ua1\"},{\"userId\":\"ua2\"},{\"userId\":\"ua3\"}]");
        stubUserStatesForIds("[\"ua1\",\"ua2\",\"ua3\"]",
                "{\"result\":[{\"userId\":\"ua1\",\"state\":\"USER_STATE_ACTIVE\"},{\"userId\":\"ua2\",\"state\":\"USER_STATE_ACTIVE\"},{\"userId\":\"ua3\",\"state\":\"USER_STATE_INITIAL\"}]}");
        // Org B: 1 active user
        stubGrantsForOrg("zit-org-b", "[{\"userId\":\"ub1\"}]");
        stubUserStatesForIds("[\"ub1\"]",
                "{\"result\":[{\"userId\":\"ub1\",\"state\":\"USER_STATE_ACTIVE\"}]}");

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
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
        // "zit-org-zitadel-only" is in Zitadel but has no local DB record
        stubListAllOrganizations(
                "[{\"id\":\"zit-org-zitadel-only\",\"name\":\"Zitadel Only Org\",\"details\":{\"creationDate\":\"2024-03-01T10:00:00Z\"}}]");
        stubGrantsForOrg("zit-org-zitadel-only", "[]");

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations[0].zitadelOrganizationId").value("zit-org-zitadel-only"))
                .andExpect(jsonPath("$.organizations[0].id").value(org.hamcrest.Matchers.nullValue()));
    }

    // New-TC2: List returns empty array when Zitadel only has the home org
    @Test
    void testListOrganizations_OnlyHomeOrgInZitadel_ReturnsEmpty() throws Exception {
        // Zitadel only has the home org — it gets filtered → empty result
        stubListAllOrganizations(
                "[{\"id\":\"test-main-org-id\",\"name\":\"Home\",\"details\":{\"creationDate\":\"2024-01-01T00:00:00Z\"}}]");

        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations").isArray())
                .andExpect(jsonPath("$.organizations").isEmpty());
    }

    // ===== DELETE /admins/organizations/{orgId} =====

    // New-TC3: Delete organization with active and invited members
    @Test
    void testDeleteOrganization_WithActiveAndInvitedMembers() throws Exception {
        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-del", "Del Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity identity1 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity identity2 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserAccountEntity account1 = userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "del-active-1", org.getId(), identity1.getId(), Instant.now(), null, new ArrayList<>()));
        UserAccountEntity account2 = userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "del-active-2", org.getId(), identity2.getId(), Instant.now(), null, new ArrayList<>()));

        stubGetOrgInfo("zit-org-del");
        stubListUsersInOrg("zit-org-del",
                "[{\"userId\":\"del-active-1\",\"state\":\"USER_STATE_ACTIVE\"}" +
                ",{\"userId\":\"del-active-2\",\"state\":\"USER_STATE_ACTIVE\"}" +
                ",{\"userId\":\"del-invited-1\",\"state\":\"USER_STATE_INITIAL\"}]");
        stubDeleteUser("del-active-1");
        stubDeleteUser("del-active-2");
        stubDeleteUser("del-invited-1");
        stubDeleteOrganization("zit-org-del");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-del")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("del-active-1").isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("del-active-2").isPresent());
    }

    // New-TC4: Delete organization with no members
    @Test
    void testDeleteOrganization_NoMembers() throws Exception {
        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-empty", "Empty Org", Instant.now());
        organizationRepository.save(org);

        stubGetOrgInfo("zit-org-empty");
        stubListUsersInOrg("zit-org-empty", "[]");
        stubDeleteOrganization("zit-org-empty");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-empty")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC5: Delete cleans up identity when it is the last account
    @Test
    void testDeleteOrganization_CleansUpIdentityForLastAccount() throws Exception {
        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-identity-clean", "Identity Clean Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity identity = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(
                UUID.randomUUID(), "solo-user", org.getId(), identity.getId(), Instant.now(), null, new ArrayList<>()));

        stubGetOrgInfo("zit-org-identity-clean");
        stubListUsersInOrg("zit-org-identity-clean", "[{\"userId\":\"solo-user\",\"state\":\"USER_STATE_ACTIVE\"}]");
        stubDeleteUser("solo-user");
        stubDeleteOrganization("zit-org-identity-clean");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-identity-clean")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isNoContent());

        assertFalse(organizationRepository.findById(org.getId()).isPresent());
        assertFalse(userAccountRepository.findByZitadelSub("solo-user").isPresent());
        assertFalse(userIdentityRepository.findById(identity.getId()).isPresent());
    }

    // New-TC6: Delete preserves identity when user has account in another org
    @Test
    void testDeleteOrganization_PreservesIdentityWithRemainingAccount() throws Exception {
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

        stubGetOrgInfo("zit-org-to-delete");
        stubListUsersInOrg("zit-org-to-delete", "[{\"userId\":\"linked-user\",\"state\":\"USER_STATE_ACTIVE\"}]");
        stubDeleteUser("linked-user");
        stubDeleteOrganization("zit-org-to-delete");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-to-delete")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isNoContent());

        assertFalse(userAccountRepository.findByZitadelSub("linked-user").isPresent());
        assertTrue(userIdentityRepository.findById(identity.getId()).isPresent());
        assertTrue(userAccountRepository.findByZitadelSub("linked-user-other-account").isPresent());
    }

    // New-TC7: Organization not found → 404
    @Test
    void testDeleteOrganization_NotFound() throws Exception {
        String randomId = UUID.randomUUID().toString();
        stubGetOrgInfoNotFound(randomId);

        mockMvc.perform(delete("/admins/organizations/" + randomId)
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    // New-TC8: Partial failure — one user delete fails, org not deleted
    @Test
    void testDeleteOrganization_PartialFailure() throws Exception {
        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-partial", "Partial Org", Instant.now());
        organizationRepository.save(org);

        UserIdentityEntity id1 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity id2 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        UserIdentityEntity id3 = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-1", org.getId(), id1.getId(), Instant.now(), null, new ArrayList<>()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-2", org.getId(), id2.getId(), Instant.now(), null, new ArrayList<>()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "partial-user-3", org.getId(), id3.getId(), Instant.now(), null, new ArrayList<>()));

        stubGetOrgInfo("zit-org-partial");
        stubListUsersInOrg("zit-org-partial",
                "[{\"userId\":\"partial-user-1\",\"state\":\"USER_STATE_ACTIVE\"}" +
                ",{\"userId\":\"partial-user-2\",\"state\":\"USER_STATE_ACTIVE\"}" +
                ",{\"userId\":\"partial-user-3\",\"state\":\"USER_STATE_ACTIVE\"}]");
        stubDeleteUser("partial-user-1");
        stubDeleteUserFails("partial-user-2");
        stubDeleteUser("partial-user-3");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-partial")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("PARTIAL_DELETION_FAILURE"))
                .andExpect(jsonPath("$.failedUserIds[0]").value("partial-user-2"));

        // Org must not be deleted — local record preserved for retry
        assertTrue(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC9: Zitadel org delete fails → 502, local record preserved
    @Test
    void testDeleteOrganization_ZitadelOrgDeleteFails() throws Exception {
        OrganizationEntity org = new OrganizationEntity(UUID.randomUUID(), "zit-org-fail", "Fail Org", Instant.now());
        organizationRepository.save(org);

        stubGetOrgInfo("zit-org-fail");
        stubListUsersInOrg("zit-org-fail", "[]");
        stubDeleteOrganizationFails("zit-org-fail");

        mockMvc.perform(delete("/admins/organizations/" + "zit-org-fail")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isBadGateway());

        // Local record must still exist so the admin can retry
        assertTrue(organizationRepository.findById(org.getId()).isPresent());
    }

    // New-TC: Deleting home org is forbidden
    @Test
    void testDeleteOrganization_HomeOrgForbidden() throws Exception {
        // Create a local record whose zitadelOrgId matches the configured home org
        OrganizationEntity homeOrg = new OrganizationEntity(UUID.randomUUID(), "test-main-org-id", "Home", Instant.now());
        organizationRepository.save(homeOrg);

        stubGetOrgInfo("test-main-org-id");

        mockMvc.perform(delete("/admins/organizations/" + "test-main-org-id")
                .with(jwt().jwt(buildJwt("super-admin-user", "SUPER_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isForbidden());

        // Local record must still exist
        assertTrue(organizationRepository.findById(homeOrg.getId()).isPresent());
    }

    // New-TC10: Access as COMPANY_ADMIN → 403
    @Test
    void testDeleteOrganization_ForbiddenForCompanyAdmin() throws Exception {
        mockMvc.perform(delete("/admins/organizations/" + UUID.randomUUID())
                .with(jwt().jwt(buildJwt("company-admin-user", "COMPANY_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    // New-TC10b: Access GET as COMPANY_ADMIN → 403
    @Test
    void testListOrganizations_ForbiddenForCompanyAdmin() throws Exception {
        mockMvc.perform(get("/admins/organizations")
                .with(jwt().jwt(buildJwt("company-admin-user", "COMPANY_ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
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

    // --- Stubs ---

    private void stubEmailNotExists() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(okJson("{\"result\": []}")));
    }

    private void stubEmailExists() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(okJson("{\"result\": [{\"userId\": \"existing-user\"}]}")));
    }

    private void stubAddOrganization(String orgId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/organizations"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(okJson("{\"organizationId\": \"" + orgId + "\"}")));
    }

    private void stubAddHumanUser(String userId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/human"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .withHeader("x-zitadel-orgid", WireMock.matching(".*"))
            .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
    }

    private void stubAddUserGrant() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/management/v1/users/.*/grants"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .withHeader("x-zitadel-orgid", WireMock.matching(".*"))
            .willReturn(ok()));
    }

    private void stubCreateInviteCode() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/.*/invite_code"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(ok()));
    }

    /** Stubs POST /v2/organizations/_search (no idQuery in body) to return all organizations. */
    private void stubListAllOrganizations(String orgArrayJson) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/organizations/_search"))
                .withRequestBody(WireMock.not(WireMock.containing("idQuery")))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(okJson("{\"result\":" + orgArrayJson + "}")));
    }

    /** Stubs POST /management/v1/users/grants/_search for a specific org to return the given result array JSON. */
    private void stubGrantsForOrg(String zitadelOrgId, String resultArrayJson) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/management/v1/users/grants/_search"))
                .withHeader("x-zitadel-orgid", WireMock.equalTo(zitadelOrgId))
                .willReturn(okJson("{\"result\":" + resultArrayJson + "}")));
    }

    /** Stubs POST /v2/users (inUserIdsQuery) to return the given full response JSON. */
    private void stubUserStatesForIds(String userIdsJson, String responseJson) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users"))
                .withRequestBody(WireMock.containing("inUserIdsQuery"))
                .withRequestBody(WireMock.containing(userIdsJson.replace("\"", "\"")))
                .willReturn(okJson(responseJson)));
    }

    /** Stubs POST /v2/users (organizationIdQuery) to return a list of users for the given org. */
    private void stubListUsersInOrg(String zitadelOrgId, String userArrayJson) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users"))
                .withRequestBody(WireMock.containing("organizationIdQuery"))
                .withRequestBody(WireMock.containing(zitadelOrgId))
                .willReturn(okJson("{\"result\":" + userArrayJson + "}")));
    }

    private void stubDeleteUser(String userId) {
        wireMockServer.stubFor(WireMock.delete(urlMatching("/v2/users/" + userId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(ok()));
    }

    private void stubDeleteUserFails(String userId) {
        wireMockServer.stubFor(WireMock.delete(urlMatching("/v2/users/" + userId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"internal\"}")));
    }

    private void stubDeleteOrganization(String zitadelOrgId) {
        wireMockServer.stubFor(WireMock.delete(urlMatching("/v2/organizations/" + zitadelOrgId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(ok()));
    }

    private void stubDeleteOrganizationFails(String zitadelOrgId) {
        wireMockServer.stubFor(WireMock.delete(urlMatching("/v2/organizations/" + zitadelOrgId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"internal\"}")));
    }

    private void stubGetOrgInfo(String zitadelOrgId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/organizations/_search"))
                .withRequestBody(WireMock.containing("idQuery"))
                .withRequestBody(WireMock.containing(zitadelOrgId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(okJson("{\"result\":[{\"id\":\"" + zitadelOrgId + "\",\"name\":\"Test Org\"}]}")));
    }

    private void stubGetOrgInfoNotFound(String zitadelOrgId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/organizations/_search"))
                .withRequestBody(WireMock.containing("idQuery"))
                .withRequestBody(WireMock.containing(zitadelOrgId))
                .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
                .willReturn(okJson("{\"result\":[]}")));
    }

    // --- JWT builders ---

    private Jwt buildJwt(String sub, String role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role, new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", sub + "@example.com")
            .claim("urn:zitadel:iam:user:resourceowner:id", "org-admin")
            .claim("urn:zitadel:iam:user:resourceowner:name", "Admin Org")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    private Jwt buildJwtForInvitedUser(String sub, String email, String zitadelOrgId) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("COMPANY_ADMIN", Map.of());

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
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
