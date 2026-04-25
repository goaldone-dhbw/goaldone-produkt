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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class SuperAdminIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

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
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void testListSuperAdmins_Success() throws Exception {
        stubListAuthorizations(List.of("admin-1", "admin-2"));
        stubGetUser("admin-1", "admin1@goaldone.de");
        stubGetUser("admin-2", "admin2@goaldone.de");

        mockMvc.perform(get("/admins/super-admins")
            .with(jwt().jwt(buildJwt("caller", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].zitadelId").value("admin-1"))
            .andExpect(jsonPath("$[0].email").value("admin1@goaldone.de"));
    }

    @Test
    void testInviteSuperAdmin_Success() throws Exception {
        stubEmailNotExists();
        stubAddHumanUser("new-admin-id");
        stubAddUserGrant();
        stubCreateInviteCode();

        Map<String, String> request = Map.of("email", "new@goaldone.de");

        mockMvc.perform(post("/admins/super-admins")
            .with(jwt().jwt(buildJwt("caller", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void testDeleteSuperAdmin_Success() throws Exception {
        // Arrange: 2 admins exist, delete one
        stubListAuthorizations(List.of("admin-1", "admin-2"));
        stubDeleteUser("admin-2");

        // Local shadow record
        UserIdentityEntity identity = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity(UUID.randomUUID(), "test-main-org-id", "Goaldone", Instant.now()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "admin-2", org.getId(), identity.getId(), Instant.now(), Instant.now()));

        mockMvc.perform(delete("/admins/super-admins/admin-2")
            .with(jwt().jwt(buildJwt("admin-1", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isNoContent());

        assertEquals(0, userAccountRepository.count());
        assertFalse(userAccountRepository.findByZitadelSub("admin-2").isPresent());
    }

    @Test
    void testDeleteSuperAdmin_PreventsLastAdminDeletion() throws Exception {
        stubListAuthorizations(List.of("admin-1"));

        mockMvc.perform(delete("/admins/super-admins/admin-1")
            .with(jwt().jwt(buildJwt("admin-1", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("LAST_SUPER_ADMIN_CANNOT_BE_DELETED"));
    }

    @Test
    void testAccess_ForbiddenForRegularUser() throws Exception {
        mockMvc.perform(get("/admins/super-admins")
            .with(jwt().jwt(buildJwt("user", "USER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    // --- Stubs ---

    private void stubListAuthorizations(List<String> userIds) {
        StringBuilder result = new StringBuilder("{\"result\": [");
        for (int i = 0; i < userIds.size(); i++) {
            result.append("{\"userId\": \"").append(userIds.get(i)).append("\"}");
            if (i < userIds.size() - 1) result.append(",");
        }
        result.append("]}");

        wireMockServer.stubFor(WireMock.post(urlPathMatching("/management/v1/users/grants/_search"))
            .willReturn(okJson(result.toString())));
    }

    private void stubGetUser(String userId, String email) {
        String userJson = String.format("""
            {
                "user": {
                    "id": "%s",
                    "email": { "email": "%s" },
                    "profile": { "givenName": "Admin", "familyName": "User" },
                    "state": "USER_STATE_ACTIVE",
                    "details": { "createdDate": "2023-10-27T10:00:00Z" }
                }
            }
            """, userId, email);

        wireMockServer.stubFor(WireMock.get(urlPathEqualTo("/v2/users/" + userId))
            .willReturn(okJson(userJson)));
    }

    private void stubDeleteUser(String userId) {
        wireMockServer.stubFor(WireMock.delete(urlPathEqualTo("/v2/users/" + userId))
            .willReturn(ok()));
    }

    private void stubEmailNotExists() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users"))
            .willReturn(okJson("{\"result\": []}")));
    }

    private void stubAddHumanUser(String userId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/human"))
            .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
    }

    private void stubAddUserGrant() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/management/v1/users/.*/grants"))
            .willReturn(ok()));
    }

    private void stubCreateInviteCode() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/.*/invite_code"))
            .willReturn(ok()));
    }

    private Jwt buildJwt(String sub, String role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role, new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuer("http://localhost:8099")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claims(c -> c.putAll(claims.getClaims()))
            .build();
    }
}
