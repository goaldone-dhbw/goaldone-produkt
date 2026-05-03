package de.goaldone.backend.controller;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for GET /me/organizations endpoint.
 * Verifies D-09 (renamed endpoint), D-10 (response structure), D-12 (old endpoint removed).
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserOrganizationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID userId;
    private UUID orgId;
    private UUID membershipId;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

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

    /**
     * Test 1 (D-09): GET /me/organizations returns 200 with organizations array.
     */
    @Test
    void getMyOrganizations_withValidJwt_returns200WithOrganizationsKey() throws Exception {
        mockMvc.perform(get("/me/organizations")
                .with(jwt().jwt(j -> j
                    .claim("user_id", userId.toString())
                    .claim("primary_email", "test@example.com"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizations").isArray());
    }

    /**
     * Test 2 (D-10): GET /me/organizations response contains per-org entries with required fields.
     */
    @Test
    void getMyOrganizations_returnsAllMemberships_withRequiredFields() throws Exception {
        mockMvc.perform(get("/me/organizations")
                .with(jwt().jwt(j -> j
                    .claim("user_id", userId.toString())
                    .claim("primary_email", "test@example.com"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizations[0].accountId").exists())
            .andExpect(jsonPath("$.organizations[0].organizationId").exists())
            .andExpect(jsonPath("$.organizations[0].organizationName").exists())
            .andExpect(jsonPath("$.organizations[0].roles").isArray())
            .andExpect(jsonPath("$.organizations[0].hasConflicts").isBoolean());
    }

    /**
     * Test 3 (D-12): Old GET /users/accounts endpoint returns 404 (removed).
     */
    @Test
    void getMyAccounts_oldPath_returns404() throws Exception {
        mockMvc.perform(get("/users/accounts")
                .with(jwt()))
            .andExpect(status().isNotFound());
    }

    /**
     * Test 4 (T-08-01-01): GET /me/organizations without Authorization header returns 401.
     */
    @Test
    void getMyOrganizations_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/me/organizations"))
            .andExpect(status().isUnauthorized());
    }
}
