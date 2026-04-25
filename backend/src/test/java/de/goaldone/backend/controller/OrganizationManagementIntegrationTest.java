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

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
