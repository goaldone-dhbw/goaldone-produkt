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

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class AccountLinkingIntegrationTest {

    // Reuse the shared WireMockServer from TestControllerIntegrationTest
    private static final WireMockServer wireMockServer = TestControllerIntegrationTest.getSharedWireMockServer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private LinkTokenRepository linkTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AccountLinkingService accountLinkingService;

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

        linkTokenRepository.deleteAll();
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();
    }

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

    private UserAccountEntity provisionAccount(String sub, String email, String givenName, String familyName,
                                                String zitadelOrgId, String orgName) throws Exception {
        stubZitadelUserInfo(email, givenName, familyName);
        mockMvc.perform(get("/test/me")
            .with(jwt()
                .jwt(buildJwt(sub, email, givenName, familyName, zitadelOrgId, orgName))))
            .andExpect(status().isOk());
        return userAccountRepository.findByZitadelSub(sub).orElseThrow();
    }

    // TC01: Request link returns 201 with token
    @Test
    void testRequestAccountLink() throws Exception {
        provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        mockMvc.perform(post("/accounts/links/request")
            .with(jwt()
                .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.linkToken", notNullValue()))
            .andExpect(jsonPath("$.expiresAt", notNullValue()));

        assertEquals(1, linkTokenRepository.count());
    }

    // TC02: Request without auth returns 401
    @Test
    void testRequestAccountLinkUnauthorized() throws Exception {
        mockMvc.perform(post("/accounts/links/request"))
            .andExpect(status().isUnauthorized());
    }

    // TC03: Confirm link merges identities
    @Test
    void testConfirmLinkDifferentOrgs() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isNoContent());

        UserAccountEntity reloadedB = userAccountRepository.findById(b.getId()).orElseThrow();
        assertEquals(a.getUserIdentityId(), reloadedB.getUserIdentityId());
        assertEquals(0, linkTokenRepository.count());
    }

    // TC04: Expired token returns 410
    @Test
    void testConfirmLinkExpiredToken() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().minusSeconds(1));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isGone());
    }

    // TC05: Non-existent token returns 410
    @Test
    void testConfirmLinkNonExistentToken() throws Exception {
        provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", UUID.randomUUID().toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isGone());
    }

    // TC06: Already linked returns 409
    @Test
    void testConfirmLinkAlreadyLinked() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isNoContent());

        // Try again -> 409
        UUID token2Id = UUID.randomUUID();
        LinkTokenEntity token2 = new LinkTokenEntity();
        token2.setToken(token2Id);
        token2.setInitiatorAccountId(a.getId());
        token2.setExpiresAt(Instant.now().plusSeconds(600));
        token2.setCreatedAt(Instant.now());
        linkTokenRepository.save(token2);

        Map<String, String> req2 = new HashMap<>();
        req2.put("linkToken", token2Id.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req2))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isConflict());
    }

    // TC07: Same org returns 409
    @Test
    void testConfirmLinkSameOrg() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-same", "Org Same");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-same", "Org Same");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-same", "Org Same"))))
            .andExpect(status().isConflict());
    }

    // TC08: GET accounts single
    @Test
    void testGetMyAccountsSingle() throws Exception {
        provisionAccount("sub-1", "a@example.com", "User", "A", "org-1", "Org 1");

        mockMvc.perform(get("/users/accounts")
            .with(jwt()
                .jwt(buildJwt("sub-1", "a@example.com", "User", "A", "org-1", "Org 1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts", hasSize(1)))
            .andExpect(jsonPath("$.accounts[0].organizationName", is("Org 1")));
    }

    // TC09: GET accounts after link
    @Test
    void testGetMyAccountsAfterLink() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/accounts")
            .with(jwt()
                .jwt(buildJwt("sub-a", "a@example.com", "User", "A", "org-a", "Org A"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.accounts[*].organizationName", containsInAnyOrder("Org A", "Org B")));
    }

    // TC10: Unlink happy path
    @Test
    void testUnlinkAccount() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/accounts/links/" + b.getId())
            .with(jwt()
                .jwt(buildJwt("sub-a", "a@example.com", "User", "A", "org-a", "Org A"))))
            .andExpect(status().isNoContent());

        UserAccountEntity reloadedB = userAccountRepository.findById(b.getId()).orElseThrow();
        assertNotEquals(a.getUserIdentityId(), reloadedB.getUserIdentityId());
    }

    // TC11: Unlink different identity 403
    @Test
    void testUnlinkDifferentIdentity() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        mockMvc.perform(delete("/accounts/links/" + b.getId())
            .with(jwt()
                .jwt(buildJwt("sub-a", "a@example.com", "User", "A", "org-a", "Org A"))))
            .andExpect(status().isForbidden());
    }

    // TC12: Unlink only account 400
    @Test
    void testUnlinkOnlyAccount() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");

        mockMvc.perform(delete("/accounts/links/" + a.getId())
            .with(jwt()
                .jwt(buildJwt("sub-a", "a@example.com", "User", "A", "org-a", "Org A"))))
            .andExpect(status().isBadRequest());
    }

    // TC13: Cleanup removes expired
    @Test
    void testCleanupExpiredTokens() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");

        LinkTokenEntity exp1 = new LinkTokenEntity();
        exp1.setToken(UUID.randomUUID());
        exp1.setInitiatorAccountId(a.getId());
        exp1.setExpiresAt(Instant.now().minusSeconds(1));
        exp1.setCreatedAt(Instant.now());
        linkTokenRepository.save(exp1);

        LinkTokenEntity valid = new LinkTokenEntity();
        valid.setToken(UUID.randomUUID());
        valid.setInitiatorAccountId(a.getId());
        valid.setExpiresAt(Instant.now().plusSeconds(600));
        valid.setCreatedAt(Instant.now());
        linkTokenRepository.save(valid);

        accountLinkingService.cleanupExpiredTokens();
        assertEquals(1, linkTokenRepository.count());
    }

    // TC14: JIT creates identity
    @Test
    void testJitCreatesIdentity() throws Exception {
        provisionAccount("sub-new", "new@example.com", "User", "New", "org-new", "Org New");

        assertEquals(1, userIdentityRepository.count());
        UserAccountEntity account = userAccountRepository.findByZitadelSub("sub-new").orElseThrow();
        assertTrue(userIdentityRepository.existsById(account.getUserIdentityId()));
    }

    // TC15: GET accounts two linked
    @Test
    void testGetMyAccountsTwoLinked() throws Exception {
        UserAccountEntity a = provisionAccount("sub-a", "a@example.com", "User", "A", "org-a", "Org A");
        UserAccountEntity b = provisionAccount("sub-b", "b@example.com", "User", "B", "org-b", "Org B");

        UUID tokenId = UUID.randomUUID();
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(tokenId);
        token.setInitiatorAccountId(a.getId());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        Map<String, String> req = new HashMap<>();
        req.put("linkToken", tokenId.toString());

        mockMvc.perform(post("/accounts/links/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req))
            .with(jwt()
                .jwt(buildJwt("sub-b", "b@example.com", "User", "B", "org-b", "Org B"))))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/accounts")
            .with(jwt()
                .jwt(buildJwt("sub-a", "a@example.com", "User", "A", "org-a", "Org A"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accounts", hasSize(2)))
            .andExpect(jsonPath("$.accounts[*].organizationName", containsInAnyOrder("Org A", "Org B")));
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
