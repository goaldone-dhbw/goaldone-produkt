package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
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
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class UserAccountDeletionServiceIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountDeletionService userAccountDeletionService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private LinkTokenRepository linkTokenRepository;

    @Autowired
    private AccountLinkingService accountLinkingService;

    private MockMvc mockMvc;
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
        linkTokenRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void deleteOnlyAccount_deletesIdentityToo() throws Exception {
        // Provision one account
        Jwt jwt = buildJwt("test-user-1", "test1@example.com", "Test", "User 1", "org-id-1", "Test Org 1");
        mockMvc.perform(get("/users/accounts").with(jwt().jwt(jwt)));

        UserAccountEntity account = userAccountRepository.findByZitadelSub("test-user-1").orElseThrow();
        UUID identityId = account.getUserIdentityId();

        // Verify identity exists
        assertTrue(userIdentityRepository.existsById(identityId));

        // Delete the account
        userAccountDeletionService.deleteUserAccount(account.getId());

        // Assert account is deleted
        assertFalse(userAccountRepository.existsById(account.getId()));

        // Assert identity is also deleted
        assertFalse(userIdentityRepository.existsById(identityId));
    }

    @Test
    void deleteOneOfLinkedAccounts_keepsIdentity() throws Exception {
        // Provision two accounts and link them
        Jwt jwt1 = buildJwt("test-user-1", "test1@example.com", "Test", "User 1", "org-id-1", "Test Org 1");
        Jwt jwt2 = buildJwt("test-user-2", "test2@example.com", "Test", "User 2", "org-id-2", "Test Org 2");

        mockMvc.perform(get("/users/accounts").with(jwt().jwt(jwt1)));
        mockMvc.perform(get("/users/accounts").with(jwt().jwt(jwt2)));

        UserAccountEntity account1 = userAccountRepository.findByZitadelSub("test-user-1").orElseThrow();
        UserAccountEntity account2 = userAccountRepository.findByZitadelSub("test-user-2").orElseThrow();
        UUID identityId = account1.getUserIdentityId();

        // Link them via the service
        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(UUID.randomUUID());
        token.setInitiatorAccountId(account1.getId());
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setCreatedAt(Instant.now());
        linkTokenRepository.save(token);

        accountLinkingService.confirmLink(token.getToken(), account2.getId());

        // Verify both are in the same identity
        UserAccountEntity a1Fresh = userAccountRepository.findById(account1.getId()).orElseThrow();
        UserAccountEntity a2Fresh = userAccountRepository.findById(account2.getId()).orElseThrow();
        assertEquals(a1Fresh.getUserIdentityId(), a2Fresh.getUserIdentityId());

        // Delete one account
        userAccountDeletionService.deleteUserAccount(account1.getId());

        // Assert deleted account is gone
        assertFalse(userAccountRepository.existsById(account1.getId()));

        // Assert the other account still exists
        assertTrue(userAccountRepository.existsById(account2.getId()));

        // Assert identity still exists
        assertTrue(userIdentityRepository.existsById(identityId));
    }

    @Test
    void deleteNonExistentAccount_throwsIllegalStateException() {
        UUID randomId = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> userAccountDeletionService.deleteUserAccount(randomId));
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
