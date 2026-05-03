package de.goaldone.backend.controller;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.model.UserOrganization;
import de.goaldone.backend.model.UserOrganizationsResponse;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import de.goaldone.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /me/organizations endpoint.
 * Verifies D-09 (renamed endpoint), D-10 (response structure), D-12 (old endpoint removed).
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class UserOrganizationsControllerTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private UUID userId;
    private UUID orgId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        membershipId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Test Organization");
        org.setCreatedAt(Instant.now());
        organizationRepository.save(org);

        MembershipEntity membership = new MembershipEntity();
        membership.setId(membershipId);
        membership.setOrganizationId(orgId);
        membership.setUser(user);
        membership.setStatus("ACTIVE");
        membership.setRole("COMPANY_ADMIN");
        membership.setCreatedAt(Instant.now());
        membershipRepository.save(membership);
    }

    @AfterEach
    void tearDown() {
        membershipRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Test 1 (D-09): buildOrganizationsResponse returns a response with non-null organizations list.
     */
    @Test
    void getMyOrganizations_withValidJwt_returnsOrganizationsKey() {
        Jwt jwt = buildJwt(userId.toString(), "test@example.com");

        UserOrganizationsResponse response = userService.buildOrganizationsResponse(jwt);

        assertNotNull(response);
        assertNotNull(response.getOrganizations());
        assertEquals(1, response.getOrganizations().size());
    }

    /**
     * Test 2 (D-10): Response contains per-org entries with all required fields populated.
     */
    @Test
    void getMyOrganizations_returnsAllMemberships_withRequiredFields() {
        Jwt jwt = buildJwt(userId.toString(), "test@example.com");

        UserOrganizationsResponse response = userService.buildOrganizationsResponse(jwt);
        UserOrganization org = response.getOrganizations().get(0);

        assertNotNull(org.getAccountId());
        assertNotNull(org.getOrganizationId());
        assertNotNull(org.getOrganizationName());
        assertNotNull(org.getRoles());
        assertTrue(org.getRoles().contains("COMPANY_ADMIN"));
        assertNotNull(org.getHasConflicts());
    }

    /**
     * Test 3 (D-12): GET /users/accounts has no handler mapping registered (endpoint removed).
     */
    @Test
    void getMyAccounts_oldPath_hasNoHandlerMapping() {
        boolean hasOldGetAccountsMapping = requestMappingHandlerMapping.getHandlerMethods()
            .keySet()
            .stream()
            .anyMatch(info -> {
                String infoStr = info.toString();
                return infoStr.contains("/users/accounts") && !infoStr.contains("links");
            });

        assertFalse(hasOldGetAccountsMapping, "GET /users/accounts should not have a handler mapping (removed per D-12)");
    }

    /**
     * Test 4 (D-09): GET /me/organizations has a handler mapping registered.
     */
    @Test
    void getMyOrganizations_path_hasHandlerMappingRegistered() {
        boolean hasMeOrgsMapping = requestMappingHandlerMapping.getHandlerMethods()
            .keySet()
            .stream()
            .anyMatch(info -> info.toString().contains("/me/organizations"));

        assertTrue(hasMeOrgsMapping, "GET /me/organizations should have a handler mapping registered (D-09)");
    }

    private Jwt buildJwt(String userId, String email) {
        return Jwt.withTokenValue("test-token")
            .headers(h -> h.put("alg", "HS256"))
            .claim("sub", userId)
            .claim("user_id", userId)
            .claim("primary_email", email)
            .claim("iat", Instant.now())
            .claim("exp", Instant.now().plusSeconds(3600))
            .build();
    }
}
