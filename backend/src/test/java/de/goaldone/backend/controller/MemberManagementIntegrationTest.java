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
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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
class MemberManagementIntegrationTest {

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

    private OrganizationEntity myOrg;
    private UserAccountEntity myAdminAccount;

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

        UserIdentityEntity identity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(identity);

        myOrg = new OrganizationEntity(UUID.randomUUID(), "org-123", "My Org", Instant.now());
        organizationRepository.save(myOrg);

        myAdminAccount = new UserAccountEntity(
                UUID.randomUUID(), "admin-sub",
                myOrg.getId(), identity.getId(),
                Instant.now(), Instant.now(), new ArrayList<>());
        userAccountRepository.save(myAdminAccount);
    }

    @Test
    void inviteMember_Success() throws Exception {
        stubEmailNotExists();
        stubAddHumanUser("user-new");
        stubAddUserGrant();
        stubCreateInviteCode();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");
        body.put("role", "USER");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    @Test
    void inviteMember_EmailConflict() throws Exception {
        stubEmailExists();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "existing@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE: existing@example.com"));
    }

    @Test
    void inviteMember_Forbidden_WrongOrg() throws Exception {
        UUID otherOrgId = UUID.randomUUID();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", otherOrgId)
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void inviteMember_Forbidden_NoAdminRole() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "USER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void reinviteMember_Success() throws Exception {
        stubGetUser("USER_STATE_INITIAL");
        stubCreateInviteCode();

        mockMvc.perform(post("/organizations/{orgId}/members/{zitadelUserId}/reinvite", myOrg.getId(), "some-user-id")
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void reinviteMember_Conflict_AlreadyActive() throws Exception {
        stubGetUser("USER_STATE_ACTIVE");

        mockMvc.perform(post("/organizations/{orgId}/members/{zitadelUserId}/reinvite", myOrg.getId(), "some-user-id")
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("USER_ALREADY_ACTIVE: some-user-id"));
    }

    // --- Stubs ---

    private void stubEmailNotExists() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users.*"))
            .willReturn(okJson("{\"result\": []}")));
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/.*"))
            .willReturn(okJson("{\"result\": []}")));
    }

    private void stubEmailExists() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users.*"))
            .willReturn(okJson("{\"result\": [{\"userId\": \"existing-user\"}]}")));
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/.*"))
            .willReturn(okJson("{\"result\": [{\"userId\": \"existing-user\"}]}")));
    }

    private void stubAddHumanUser(String userId) {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/human"))
            .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/AddHumanUser"))
            .willReturn(okJson("{\"userId\": \"" + userId + "\"}")));
    }

    private void stubAddUserGrant() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/management/v1/users/.*/grants"))
            .willReturn(ok()));
    }

    private void stubCreateInviteCode() {
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/v2/users/.*/invite_code"))
            .willReturn(ok()));
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/CreateInviteCode"))
            .willReturn(ok()));
    }

    private void stubGetUser(String state) {
        wireMockServer.stubFor(WireMock.get(urlPathMatching("/v2/users/.*"))
            .willReturn(okJson("{\"user\": {\"state\": \"" + state + "\"}}")));
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/zitadel\\.user\\.v2\\.Users/GetUser"))
            .willReturn(okJson("{\"user\": {\"state\": \"" + state + "\"}}")));
    }

    // --- JWT builder ---

    private Jwt buildJwt(String sub, String role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role, new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", sub + "@example.com")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
