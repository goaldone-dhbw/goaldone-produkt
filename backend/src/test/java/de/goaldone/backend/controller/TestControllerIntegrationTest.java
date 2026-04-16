package de.goaldone.backend.controller;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("local")
class TestControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    // TF1: First request of unknown user (JIT provisioning)
    @Test
    void testFirstRequestUnknownUserJitProvisioning() throws Exception {
        String sub = "user-unknown-1";
        String zitadelOrgId = "org-unknown-1";
        String orgName = "Test Organization";

        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test@example.com", "John", "Doe", zitadelOrgId, orgName))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zitadelSub", equalTo(sub)))
            .andExpect(jsonPath("$.email", equalTo("test@example.com")))
            .andExpect(jsonPath("$.zitadelOrganizationId", equalTo(zitadelOrgId)));

        // Verify DB state
        assertEquals(1, organizationRepository.count());
        assertEquals(1, userRepository.count());
        assertTrue(userRepository.findByZitadelSub(sub).isPresent());
        assertTrue(organizationRepository.findByZitadelOrgId(zitadelOrgId).isPresent());
    }

    // TF2: Second request of same user (no new provisioning)
    @Test
    void testSecondRequestSameUserNoNewProvisioning() throws Exception {
        String sub = "user-same-2";
        String zitadelOrgId = "org-same-2";
        String orgName = "Test Organization 2";

        // First request
        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test2@example.com", "Jane", "Smith", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Second request
        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, "test2@example.com", "Jane", "Smith", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify no duplicate records
        assertEquals(1, organizationRepository.count());
        assertEquals(1, userRepository.count());
        UserEntity user = userRepository.findByZitadelSub(sub).orElseThrow();
        assertNotNull(user.getLastSeenAt());
    }

    // TF3: New user in already existing org
    @Test
    void testNewUserExistingOrganization() throws Exception {
        String zitadelOrgId = "org-existing-3";
        String orgName = "Existing Org";

        // Create org and first user
        String sub1 = "user-existing-3a";
        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub1, "user1@example.com", "User", "One", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Second user same org
        String sub2 = "user-existing-3b";
        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub2, "user2@example.com", "User", "Two", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify
        assertEquals(1, organizationRepository.count());
        assertEquals(2, userRepository.count());
        assertTrue(userRepository.findByZitadelSub(sub2).isPresent());
    }

    // TF4: Request without authorization header
    @Test
    void testRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/test/me"))
            .andExpect(status().isUnauthorized());

        // Verify no DB writes
        assertEquals(0, organizationRepository.count());
        assertEquals(0, userRepository.count());
    }


    // TF6: Race condition during org creation
    @Test
    void testRaceConditionOrgCreation() throws Exception {
        String zitadelOrgId = "org-race-6";
        String orgName = "Race Org";

        String sub1 = "user-race-6a";
        String sub2 = "user-race-6b";

        // Make two sequential requests from the same org.
        // The unique constraint on zitadel_org_id prevents duplicates.
        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub1, "user1@race.com", "User", "One", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/test/me")
            .with(jwt()
                .jwt(buildJwt(sub2, "user2@race.com", "User", "Two", zitadelOrgId, orgName))))
            .andExpect(status().isOk());

        // Verify exactly 1 org record (due to unique constraint on zitadel_org_id)
        // and 2 user records
        assertEquals(1, organizationRepository.count());
        assertEquals(2, userRepository.count());
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
