package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.model.MemberRole;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "zitadel.goaldone.project-id=project-1",
    "zitadel.goaldone.org-id=root-org"
})
@ActiveProfiles("local")
class MemberManagementControllerIT {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID orgId;
    private String zitadelOrgId = "zitadel-org-1";
    private UserIdentityEntity callerIdentity;

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

        orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity(orgId, zitadelOrgId, "Test Org", Instant.now());
        organizationRepository.save(org);

        callerIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(callerIdentity);
    }

    // =========================================================
    // Testfall 1 – Mitgliederliste anzeigen
    // =========================================================

    @Test
    void listMembers_Success() throws Exception {
        // Arrange
        saveCallerAccount();

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "userId": "user-1", "roleKeys": ["USER"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(post(urlEqualTo("/v2/users"))
                .willReturn(okJson("""
                        {
                          "result": [
                            {
                              "id": "user-1",
                              "state": "USER_STATE_ACTIVE",
                              "human": { "email": { "email": "user1@test.com" }, "profile": { "givenName": "User", "familyName": "One" } },
                              "details": { "creationDate": "2023-01-01T00:00:00Z" }
                            }
                          ]
                        }
                        """)));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members[0].zitadelUserId").value("user-1"));
    }

    // =========================================================
    // Testfall 2 – Nutzer von USER auf COMPANY_ADMIN befördern
    // =========================================================

    @Test
    void changeMemberRole_Success() throws Exception {
        // Arrange
        saveCallerAccount();

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["USER"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(put(urlMatching("/management/v1/users/grants/grant-1"))
                .willReturn(ok()));

        Map<String, String> body = Map.of("role", "COMPANY_ADMIN");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // =========================================================
    // Testfall 3 – Nutzer von COMPANY_ADMIN auf USER degradieren (mehrere Admins)
    // =========================================================

    @Test
    void changeMemberRole_DemoteAdmin_MultipleAdmins_Success() throws Exception {
        // Arrange
        saveCallerAccount();

        // searchUserGrants: body contains userIdQuery
        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .withRequestBody(matchingJsonPath("$.queries[?(@.userIdQuery)]"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        // listAllGrants: body contains organizationIdQuery – 2 admins present
        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .withRequestBody(matchingJsonPath("$.queries[?(@.organizationIdQuery)]"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "userId": "user-1", "roleKeys": ["COMPANY_ADMIN"] },
                            { "userId": "other-admin", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(put(urlMatching("/management/v1/users/grants/grant-1"))
                .willReturn(ok()));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // =========================================================
    // Testfall 4 – Selbst-Degradierung bei mehreren Admins
    // =========================================================

    @Test
    void changeMemberRole_SelfDemotion_MultipleAdmins_Success() throws Exception {
        // Arrange
        saveCallerAccount();

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .withRequestBody(matchingJsonPath("$.queries[?(@.userIdQuery)]"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .withRequestBody(matchingJsonPath("$.queries[?(@.organizationIdQuery)]"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "userId": "caller-sub", "roleKeys": ["COMPANY_ADMIN"] },
                            { "userId": "other-admin", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(put(urlMatching("/management/v1/users/grants/grant-1"))
                .willReturn(ok()));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert – self-demotion is allowed when multiple admins exist
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/caller-sub/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // =========================================================
    // Testfall 5 – Aktiven Nutzer erfolgreich entfernen
    // =========================================================

    @Test
    void removeMember_Active_Success() throws Exception {
        // Arrange
        saveCallerAccount();

        UserIdentityEntity targetIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(targetIdentity);
        UserAccountEntity targetAccount = new UserAccountEntity(UUID.randomUUID(), "user-1", orgId, targetIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(targetAccount);

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["USER"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(delete(urlEqualTo("/v2/users/user-1"))
                .willReturn(ok()));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/user-1")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNoContent());

        assertFalse(userAccountRepository.findByZitadelSub("user-1").isPresent());
    }

    // =========================================================
    // Testfall 6 – Eingeladenen Nutzer entfernen
    // =========================================================

    @Test
    void removeMember_Invited_Success() throws Exception {
        // Arrange – invited user has no local UserAccountEntity
        saveCallerAccount();

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["USER"] }
                          ]
                        }
                        """)));

        wireMockServer.stubFor(delete(urlEqualTo("/v2/users/invited-user"))
                .willReturn(ok()));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/invited-user")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNoContent());
    }

    // =========================================================
    // Testfall 7 – Letzten Admin degradieren → 409
    // =========================================================

    @Test
    void changeMemberRole_LastAdmin_Conflict() throws Exception {
        // Arrange
        saveCallerAccount();

        // Same stub covers both searchUserGrants and listAllGrants – only 1 admin in org
        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "userId": "user-1", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // Testfall 8 – Letzten Admin entfernen → 409
    // =========================================================

    @Test
    void removeMember_LastAdmin_Conflict() throws Exception {
        // Arrange
        saveCallerAccount();

        // Same stub covers both searchUserGrants and listAllGrants – only 1 admin in org
        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "userId": "user-1", "roleKeys": ["COMPANY_ADMIN"] }
                          ]
                        }
                        """)));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/user-1")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // Testfall 8 (negativ) – Selbst-Entfernung nicht möglich → 403
    // =========================================================

    @Test
    void removeMember_Self_Forbidden() throws Exception {
        // Arrange
        saveCallerAccount();

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/caller-sub")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 10 – Admin versucht andere Org zu verwalten → 403
    // =========================================================

    @Test
    void listMembers_WrongOrg_Forbidden() throws Exception {
        // Arrange – caller belongs to a different org
        UUID differentOrgId = UUID.randomUUID();
        OrganizationEntity otherOrg = new OrganizationEntity(differentOrgId, "zitadel-org-2", "Other Org", Instant.now());
        organizationRepository.save(otherOrg);
        UserAccountEntity callerAccount = new UserAccountEntity(UUID.randomUUID(), "caller-sub", differentOrgId, callerIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(callerAccount);

        // Act & Assert – caller is COMPANY_ADMIN of otherOrg, not of orgId
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 11 – Rolle auf gleiche Rolle ändern → 400
    // =========================================================

    @Test
    void changeMemberRole_RoleUnchanged_BadRequest() throws Exception {
        // Arrange
        saveCallerAccount();

        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": [
                            { "grantId": "grant-1", "roleKeys": ["USER"] }
                          ]
                        }
                        """)));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================
    // Testfall 12 – Zugriff als USER → 403
    // =========================================================

    @Test
    void listMembers_AsUser_Forbidden() throws Exception {
        // Act & Assert – @PreAuthorize("hasRole('COMPANY_ADMIN')") rejects USER role
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(userJwt("user-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 13 – Zugriff ohne Token → 401
    // =========================================================

    @Test
    void listMembers_NoToken_Unauthorized() throws Exception {
        // Act & Assert – no JWT provided
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // Testfall 14 – Nutzer nicht in der Org → 404
    // =========================================================

    @Test
    void removeMember_UserNotFoundInOrg_NotFound() throws Exception {
        // Arrange
        saveCallerAccount();

        // searchUserGrants returns empty result → user has no grant in this project
        wireMockServer.stubFor(post(urlEqualTo("/management/v1/users/grants/_search"))
                .willReturn(okJson("""
                        {
                          "result": []
                        }
                        """)));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/unknown-user")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // Helpers
    // =========================================================

    /** Saves a caller account for "caller-sub" in orgId using the shared callerIdentity. */
    private void saveCallerAccount() {
        UserAccountEntity callerAccount = new UserAccountEntity(UUID.randomUUID(), "caller-sub", orgId, callerIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(callerAccount);
    }

    /** Post-processor: COMPANY_ADMIN JWT with correct Spring Security authority. */
    private RequestPostProcessor adminJwt(String sub) {
        return jwt().jwt(buildJwt(sub, MemberRole.COMPANY_ADMIN))
                .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"));
    }

    /** Post-processor: USER JWT with correct Spring Security authority. */
    private RequestPostProcessor userJwt(String sub) {
        return jwt().jwt(buildJwt(sub, MemberRole.USER))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Jwt buildJwt(String sub, MemberRole role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role.getValue(), Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
