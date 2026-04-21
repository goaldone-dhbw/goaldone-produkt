package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import wiremock.com.google.common.net.HttpHeaders;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "zitadel.management-api-url=http://localhost:8099",
    "zitadel.service-account-token=test-token",
    "zitadel.goaldone.project-id=test-project-id"
})
@ActiveProfiles("local")
class OrganizationManagementIntegrationTest {

    private static final WireMockServer wireMockServer = TestControllerIntegrationTest.getSharedWireMockServer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

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

        userAccountRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    // TC1: Happy path - successfully create organization
    @Test
    void testTC1_CreateOrganizationSuccess() throws Exception {
        // Stub Zitadel responses
        stubEmailNotExists();
        stubAddOrganization("org-123");
        stubAddHumanUser("user-xyz");
        stubAddUserGrant();
        stubCreateInviteCode();

        String requestBody = objectMapper.writeValueAsString(Map.of(
            "name", "GoalDone GmbH",
            "adminEmail", "admin@goaldone.de"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.zitadelOrganizationId").value("org-123"))
            .andExpect(jsonPath("$.name").value("GoalDone GmbH"))
            .andExpect(jsonPath("$.adminEmail").value("admin@goaldone.de"))
            .andExpect(jsonPath("$.createdAt").exists());

        // Verify local DB state
        assertEquals(1, organizationRepository.count());
        assertTrue(organizationRepository.findByZitadelOrgId("org-123").isPresent());
    }

    // TC3: Email already exists (conflict)
    @Test
    void testTC3_EmailAlreadyExists() throws Exception {
        stubEmailExists();

        String requestBody = objectMapper.writeValueAsString(Map.of(
            "name", "GoalDone GmbH",
            "adminEmail", "existing@example.com"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE"));

        // Verify no Zitadel organization was created
        assertEquals(0, organizationRepository.count());
    }

    // TC4: Organization name already exists (conflict)
    @Test
    void testTC4_OrganizationNameAlreadyExists() throws Exception {
        // Pre-populate org with same name
        organizationRepository.save(new de.goaldone.backend.entity.OrganizationEntity(
            java.util.UUID.randomUUID(),
            "org-existing",
            "GoalDone GmbH",
            Instant.now()
        ));

        stubEmailNotExists();

        String requestBody = objectMapper.writeValueAsString(Map.of(
            "name", "GoalDone GmbH",
            "adminEmail", "admin@goaldone.de"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("ORGANIZATION_NAME_ALREADY_EXISTS"));
    }

    // TC8: Access without SUPER_ADMIN role (forbidden)
    @Test
    void testTC8_ForbiddenWithoutSuperAdminRole() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "name", "GoalDone GmbH",
            "adminEmail", "admin@goaldone.de"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithoutSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
    }

    // TC9: Invalid request data (validation error)
    @Test
    void testTC9_InvalidRequestData() throws Exception {
        String requestBodyEmptyName = objectMapper.writeValueAsString(Map.of(
            "name", "",
            "adminEmail", "admin@goaldone.de"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBodyEmptyName))
            .andExpect(status().isBadRequest());

        String requestBodyInvalidEmail = objectMapper.writeValueAsString(Map.of(
            "name", "GoalDone GmbH",
            "adminEmail", "invalid-email"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBodyInvalidEmail))
            .andExpect(status().isBadRequest());
    }

    // TC2: JIT provisioning on first login of invited admin
    @Test
    void testTC2_JitProvisioningOnFirstLoginOfInvitedAdmin() throws Exception {
        // First create an organization
        stubEmailNotExists();
        stubAddOrganization("org-jit-123");
        stubAddHumanUser("user-jit-xyz");
        stubAddUserGrant();
        stubCreateInviteCode();

        String requestBody = objectMapper.writeValueAsString(Map.of(
            "name", "JIT Test Org",
            "adminEmail", "admin-jit@example.com"
        ));

        mockMvc.perform(post("/admins/organizations")
            .with(jwt().jwt(buildJwtWithSuperAdminRole()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isCreated());

        // Now simulate the invited user's first login (JIT provisioning)
        stubZitadelUserInfo("admin-jit@example.com", "Admin", "Jit");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/test/me")
            .with(jwt().jwt(buildJwtForInvitedUser("user-jit-xyz", "admin-jit@example.com", "org-jit-123"))))
            .andExpect(status().isOk());

        // Verify JIT created a user_accounts record (not a new organization)
        assertEquals(1, organizationRepository.count());
        assertEquals(1, userAccountRepository.count());
        Optional<UserAccountEntity> account = userAccountRepository.findByZitadelSub("user-jit-xyz");
        assertTrue(account.isPresent());
        assertEquals("org-jit-123", organizationRepository.findById(account.get().getOrganizationId()).get().getZitadelOrgId());
    }

    // Helper: Stub Zitadel responses
    private void stubEmailNotExists() {
        wireMockServer.stubFor(WireMock.get(urlMatching("/v2/users\\?.*"))
            .willReturn(okJson("{\"result\": []}")));
    }

    private void stubEmailExists() {
        wireMockServer.stubFor(WireMock.get(urlMatching("/v2/users\\?.*"))
            .willReturn(okJson("{\"result\": [{\"userId\": \"existing-user\"}]}")));
    }

    private void stubAddOrganization(String orgId) {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/v2/organizations"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(okJson("{\"organizationId\": \"" + orgId + "\"}")));
    }

    private void stubAddHumanUser(String userId) {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/v2/users/human"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
    }

    private void stubAddUserGrant() {
        wireMockServer.stubFor(WireMock.post(urlMatching("/management/v1/users/.*/grants"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(ok()));
    }

    private void stubCreateInviteCode() {
        wireMockServer.stubFor(WireMock.post(urlMatching("/v2/users/.*/invite"))
            .withHeader(HttpHeaders.AUTHORIZATION, WireMock.containing("Bearer"))
            .willReturn(ok()));
    }

    private void stubZitadelUserInfo(String email, String givenName, String familyName) {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("email", email);
        userInfo.put("given_name", givenName);
        userInfo.put("family_name", familyName);

        try {
            wireMockServer.stubFor(
                WireMock.get(urlMatching("/oidc/v1/userinfo"))
                    .willReturn(okJson(objectMapper.writeValueAsString(userInfo)))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // JWT builders
    private Jwt buildJwtWithSuperAdminRole() {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("SUPER_ADMIN", new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("super-admin-user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", "super@example.com")
            .claim("urn:zitadel:iam:user:resourceowner:id", "org-admin")
            .claim("urn:zitadel:iam:user:resourceowner:name", "Admin Org")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    private Jwt buildJwtWithoutSuperAdminRole() {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("USER", new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("user-without-admin")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", "user@example.com")
            .claim("urn:zitadel:iam:user:resourceowner:id", "org-user")
            .claim("urn:zitadel:iam:user:resourceowner:name", "User Org")
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
            .issuer("http://localhost:8080")
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
