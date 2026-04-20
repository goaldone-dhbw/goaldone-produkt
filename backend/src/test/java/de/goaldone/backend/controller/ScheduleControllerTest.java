package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import de.goaldone.backend.service.AccountLinkingService;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the ScheduleController.
 * Tests the schedule generation and retrieval endpoints with JWT authentication.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class ScheduleControllerTest {

    // Reuse the shared WireMockServer from TestControllerIntegrationTest for mocking external services
    private static final WireMockServer wireMockServer = TestControllerIntegrationTest.getSharedWireMockServer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    // Repositories for managing test data
    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private LinkTokenRepository linkTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    // Service for account linking operations
    @Autowired
    private AccountLinkingService accountLinkingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Ensure WireMock server is running for mocking external OAuth2/OIDC calls
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        // Initialize MockMvc with Spring Security filters
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Clean up test data before each test
        linkTokenRepository.deleteAll();
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();
    }


    /**
     * Stub the Zitadel userinfo endpoint to return mock user information.
     * This simulates the OAuth2 provider's response.
     */
    private void stubZitadelUserInfo(String email, String givenName, String familyName) throws Exception {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("email", email);
        userInfo.put("given_name", givenName);
        userInfo.put("family_name", familyName);

        wireMockServer.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlMatching("/oidc/v1/userinfo"))
                        .willReturn(okJson(objectMapper.writeValueAsString(userInfo)))
        );
    }

    /**
     * Build a JWT token with claims needed for authentication.
     * Used to simulate an authenticated user request.
     */
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

    /**
     * Helper method to provision a test user account.
     * Calls the /test/me endpoint to trigger user initialization in the database.
     * 
     * @return the created UserAccountEntity
     */
    private UserAccountEntity provisionAccount(String sub, String email, String givenName, String familyName,
                                               String zitadelOrgId, String orgName) throws Exception {
        stubZitadelUserInfo(email, givenName, familyName);
        mockMvc.perform(get("/test/me")
                        .with(jwt()
                                .jwt(buildJwt(sub, email, givenName, familyName, zitadelOrgId, orgName))))
                .andExpect(status().isOk());
        return userAccountRepository.findByZitadelSub(sub).orElseThrow();
    }


    /**
     * Test: Generate schedule for a single account
     * 
     * This test verifies that:
     * 1. A user account can be provisioned
     * 2. A POST request to /schedules/{accountId} with a valid JWT and request body
     *    is properly routed to the ScheduleController
     * 3. The endpoint returns 501 (Not Implemented) as expected
     */
    @Test
    void testGenerateSingleAccountSchedule() throws Exception {
        // Arrange: Create a test user account
        UserAccountEntity a = provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        // Arrange: Prepare the request body with date range for schedule generation
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("from", "2026-04-20");
        requestBody.put("to", "2026-04-30");

        // Act & Assert: Send POST request to generate schedule and verify response
        mockMvc.perform(post("/schedules/{accountId}", a.getId())
                .with(jwt()
                        .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotImplemented());
    }

    /**
     * Test: Get schedule for a single account
     * 
     * This test verifies that:
     * 1. A user account can be provisioned
     * 2. A GET request to /schedules/{accountId} with query parameters (from, to)
     *    is properly routed to the ScheduleController
     * 3. The endpoint returns 501 (Not Implemented) as expected
     */
    @Test
    void testGetSingleAccountSchedule() throws Exception {
        // Arrange: Create a test user account
        UserAccountEntity a = provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        // Act & Assert: Send GET request with date range query parameters and verify response
        mockMvc.perform(get("/schedules/{accountId}", a.getId())
                .param("from", "2026-04-20")
                .param("to", "2026-04-30")
                .with(jwt()
                        .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1"))))
                .andExpect(status().isNotImplemented());
    }

    /**
     * Test: Generate schedules for all accounts
     * 
     * This test verifies that:
     * 1. A user account can be provisioned
     * 2. A POST request to /schedules/all with a valid JWT and request body
     *    is properly routed to the ScheduleController
     * 3. The endpoint returns 501 (Not Implemented) as expected
     */
    @Test
    void testGenerateAllAccountsSchedule() throws Exception {
        // Arrange: Create a test user account
        UserAccountEntity a = provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        // Arrange: Prepare the request body with date range for schedule generation
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("from", "2026-04-20");
        requestBody.put("to", "2026-04-30");

        // Act & Assert: Send POST request to generate schedules for all accounts and verify response
        mockMvc.perform(post("/schedules/all")
                .with(jwt()
                        .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotImplemented());
    }

    /**
     * Test: Get schedules for all accounts
     * 
     * This test verifies that:
     * 1. A user account can be provisioned
     * 2. A GET request to /schedules/all with query parameters (from, to)
     *    is properly routed to the ScheduleController
     * 3. The endpoint returns 501 (Not Implemented) as expected
     */
    @Test
    void testGetAllAccountsSchedule() throws Exception {
        // Arrange: Create a test user account
        UserAccountEntity a = provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        // Act & Assert: Send GET request with date range query parameters and verify response
        mockMvc.perform(get("/schedules/all")
                .param("from", "2026-04-20")
                .param("to", "2026-04-30")
                .with(jwt()
                        .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1"))))
                .andExpect(status().isNotImplemented());
    }
}
