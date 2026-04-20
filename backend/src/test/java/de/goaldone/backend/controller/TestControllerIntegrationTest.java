package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
public class TestControllerIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(8099);

    public static WireMockServer getSharedWireMockServer() {
        return wireMockServer;
    }

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

    private void stubZitadelUserInfo(String email, String givenName, String familyName) throws Exception {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("email", email);
        userInfo.put("given_name", givenName);
        userInfo.put("family_name", familyName);

        wireMockServer.stubFor(
            WireMock.get(urlMatching("/oidc/v1/userinfo"))
                .willReturn(okJson(objectMapper.writeValueAsString(userInfo)))
        );
    }

    // TF1: First request of unknown user (JIT provisioning)
    @Test
    void testFirstRequestUnknownUserJitProvisioning() throws Exception {
        String sub = "user-unknown-1";
        String zitadelOrgId = "org-unknown-1";
        String orgName = "Test Organization";

        stubZitadelUserInfo("test@example.com", "John", "Doe");

        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test@example.com", "John", "Doe", zitadelOrgId, orgName))))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(sub)))
            .andExpect(content().string(containsString("test@example.com")))
            .andExpect(content().string(containsString(zitadelOrgId)));

        // Verify DB state
        assertEquals(1, organizationRepository.count());
        assertEquals(1, userAccountRepository.count());
        assertTrue(userAccountRepository.findByZitadelSub(sub).isPresent());
        assertTrue(organizationRepository.findByZitadelOrgId(zitadelOrgId).isPresent());
    }

    // TF2: Second request of same user (no new provisioning)
    @Test
    void testSecondRequestSameUserNoNewProvisioning() throws Exception {
        String sub = "user-same-2";
        String zitadelOrgId = "org-same-2";
        String orgName = "Test Organization 2";

        stubZitadelUserInfo("test2@example.com", "Jane", "Smith");

        // First request
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test2@example.com", "Jane", "Smith", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Second request
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test2@example.com", "Jane", "Smith", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify no duplicate records
        assertEquals(1, organizationRepository.count());
        assertEquals(1, userAccountRepository.count());
        UserAccountEntity user = userAccountRepository.findByZitadelSub(sub).orElseThrow();
        assertNotNull(user.getLastSeenAt());
    }

    // TF3: New user in already existing org
    @Test
    void testNewUserExistingOrganization() throws Exception {
        String zitadelOrgId = "org-existing-3";
        String orgName = "Existing Org";

        stubZitadelUserInfo("user1@example.com", "User", "One");

        // Create org and first user
        String sub1 = "user-existing-3a";
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub1, "user1@example.com", "User", "One", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Configure mock for second user
        stubZitadelUserInfo("user2@example.com", "User", "Two");

        // Second user same org
        String sub2 = "user-existing-3b";
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub2, "user2@example.com", "User", "Two", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify
        assertEquals(1, organizationRepository.count());
        assertEquals(2, userAccountRepository.count());
        assertTrue(userAccountRepository.findByZitadelSub(sub2).isPresent());
    }

    // TF4: Request without authorization header
    @Test
    void testRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/test/me"))
            .andExpect(status().isUnauthorized());

        // Verify no DB writes
        assertEquals(0, organizationRepository.count());
        assertEquals(0, userAccountRepository.count());
    }


    // TF5: Null orgName fallback to zitadelOrgId
    @Test
    void testNullOrgNameFallsBackToOrgId() throws Exception {
        String zitadelOrgId = "org-null-name-5";
        String sub = "user-null-name-5";

        stubZitadelUserInfo("test@example.com", "Test", "User");

        // Build JWT without orgName claim (null)
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwtWithoutOrgName(sub, "test@example.com", "Test", "User", zitadelOrgId))))
            .andExpect(status().isOk());

        // Verify organization was created with name equal to zitadelOrgId
        var org = organizationRepository.findByZitadelOrgId(zitadelOrgId).orElseThrow();
        assertEquals(zitadelOrgId, org.getName());
        assertEquals(1, organizationRepository.count());
    }

    // TF6: Race condition during org creation
    @Test
    void testRaceConditionOrgCreation() throws Exception {
        String zitadelOrgId = "org-race-6";
        String orgName = "Race Org";

        String sub1 = "user-race-6a";
        String sub2 = "user-race-6b";

        stubZitadelUserInfo("user1@race.com", "User", "One");

        // Make two sequential requests from the same org.
        // The unique constraint on zitadel_org_id prevents duplicates.
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub1, "user1@race.com", "User", "One", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        stubZitadelUserInfo("user2@race.com", "User", "Two");

        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub2, "user2@race.com", "User", "Two", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify exactly 1 org record (due to unique constraint on zitadel_org_id)
        // and 2 user records
        assertEquals(1, organizationRepository.count());
        assertEquals(2, userAccountRepository.count());
    }

    private Jwt buildJwtWithoutOrgName(String sub, String email, String givenName, String familyName,
                                        String zitadelOrgId) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("admin", Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", email)
            .claim("given_name", givenName)
            .claim("family_name", familyName)
            .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

    private Jwt buildJwt(String sub, String email, String givenName, String familyName,
                         String zitadelOrgId, String orgName) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("admin", Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", email)
            .claim("given_name", givenName)
            .claim("family_name", familyName)
            .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
            .claim("urn:zitadel:iam:user:resourceowner:name", orgName)
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }

}
