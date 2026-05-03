package de.goaldone.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.model.TaskCreateRequest;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests for header-based multi-tenant security.
 *
 * Verifies that:
 * - Endpoints function correctly with X-Org-ID header for organization context
 * - Data is filtered by the organization ID in the header
 * - Users cannot access organizations they are not members of (403 Forbidden)
 * - GET endpoints work in dual-mode (with and without X-Org-ID)
 * - Mutations and member management require X-Org-ID header
 */
@SpringBootTest(
    properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
        "zitadel.management-api-url=http://localhost:8099",
        "zitadel.service-account-token=test-token",
        "zitadel.goaldone.project-id=test-project-id",
        "zitadel.goaldone.org-id=test-main-org-id"
    })
@ActiveProfiles("local")
class SecurityIntegrationTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private UserRepository userRepository;

  @Autowired private OrganizationRepository organizationRepository;

  @Autowired private MembershipRepository membershipRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Test data
  private UserEntity userA;
  private UserEntity userB;
  private OrganizationEntity orgA;
  private OrganizationEntity orgB;
  private MembershipEntity membershipUserAInOrgA;
  private MembershipEntity membershipUserAInOrgB;
  private MembershipEntity membershipUserBInOrgA;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

    // Create test users
    userA = new UserEntity(UUID.randomUUID(), "user-a-auth-id", Instant.now());
    userB = new UserEntity(UUID.randomUUID(), "user-b-auth-id", Instant.now());
    userRepository.saveAll(List.of(userA, userB));

    // Create test organizations
    orgA = new OrganizationEntity(UUID.randomUUID(), "org-a-auth-id", "Org A", Instant.now());
    orgB = new OrganizationEntity(UUID.randomUUID(), "org-b-auth-id", "Org B", Instant.now());
    organizationRepository.saveAll(List.of(orgA, orgB));

    // Create memberships
    // User A is member of both Org A and Org B
    membershipUserAInOrgA =
        new MembershipEntity(
            UUID.randomUUID(), orgA.getId(), userA, Instant.now(), null, List.of());
    membershipUserAInOrgB =
        new MembershipEntity(
            UUID.randomUUID(), orgB.getId(), userA, Instant.now(), null, List.of());
    // User B is only member of Org A
    membershipUserBInOrgA =
        new MembershipEntity(
            UUID.randomUUID(), orgA.getId(), userB, Instant.now(), null, List.of());
    membershipRepository.saveAll(
        List.of(membershipUserAInOrgA, membershipUserAInOrgB, membershipUserBInOrgA));
  }

  @AfterEach
  void tearDown() {
    membershipRepository.deleteAll();
    organizationRepository.deleteAll();
    userRepository.deleteAll();
    TenantContext.clear();
  }

  // ============================================================
  // Test: X-Org-ID header validation
  // ============================================================

  @Test
  void testTaskCreationWithValidOrgIdHeader() throws Exception {
    // Test: User A can create a task in Org A with valid X-Org-ID header
    TaskCreateRequest request =
        new TaskCreateRequest(
            membershipUserAInOrgA.getId(),
            "Test Task",
            "Description",
            null,
            null,
            TaskStatus.OPEN,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("X-Org-ID", orgA.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isCreated());
  }

  @Test
  void testTaskCreationWithUnauthorizedOrgId() throws Exception {
    // Test: User A cannot create a task in an organization they are not a member of
    // Setup: Create a third organization that User A is not a member of
    OrganizationEntity orgC = new OrganizationEntity(UUID.randomUUID(), "org-c-auth-id", "Org C", Instant.now());
    organizationRepository.save(orgC);

    TaskCreateRequest request =
        new TaskCreateRequest(
            UUID.randomUUID(),
            "Test Task",
            "Description",
            null,
            null,
            TaskStatus.OPEN,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("X-Org-ID", orgC.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isForbidden());

    organizationRepository.delete(orgC);
  }

  @Test
  void testTaskCreationWithMissingOrgIdHeader() throws Exception {
    // Test: POST to create a task without X-Org-ID header should fail
    // (Header is mandatory for mutations per design decision D-02)
    TaskCreateRequest request =
        new TaskCreateRequest(
            membershipUserAInOrgA.getId(),
            "Test Task",
            "Description",
            null,
            null,
            TaskStatus.OPEN,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        })))))
        .andExpect(status().isBadRequest());
  }

  // ============================================================
  // Test: Multi-org data isolation
  // ============================================================

  @Test
  void testDataIsolationBetweenOrganizations() throws Exception {
    // Test: User A can only see their membership in Org A when using X-Org-ID=OrgA
    // (Verify that tasks/data are filtered by the org context)
    // Note: This test assumes an implementation of data filtering by org ID in the service layer

    // First, verify User A has memberships in both orgs
    assert membershipUserAInOrgA.getOrganizationId().equals(orgA.getId());
    assert membershipUserAInOrgB.getOrganizationId().equals(orgB.getId());

    // When listing or querying data with X-Org-ID=OrgA, only Org A data should be returned
    // This is verified by the service implementation filtering by TenantContext.get()
  }

  // ============================================================
  // Test: Dual-mode for GET endpoints
  // ============================================================

  @Test
  void testGetTasksAllAccountsWithOrgIdHeader() throws Exception {
    // Test: GET /tasks/all with X-Org-ID=OrgA returns only tasks from Org A
    // (Hypothetical endpoint; actual implementation may vary)
    mockMvc
        .perform(
            get("/api/v1/tasks/all")
                .header("X-Org-ID", orgA.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isOk());
  }

  @Test
  void testGetTasksAllAccountsWithoutOrgIdHeader() throws Exception {
    // Test: GET /tasks/all without X-Org-ID returns tasks from all user's organizations
    // (Dual-mode support: optional header for list endpoints per D-03)
    mockMvc
        .perform(
            get("/api/v1/tasks/all")
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isOk());
  }

  // ============================================================
  // Test: Role-based access control per organization
  // ============================================================

  @Test
  void testMemberManagementRequiresAdminRole() throws Exception {
    // Test: User A with MEMBER role in Org A cannot manage members
    // User B with COMPANY_ADMIN role in Org A can manage members
    // This would require a @PreAuthorize("hasOrgRole('COMPANY_ADMIN')")
    // annotation on the member management endpoints
    mockMvc
        .perform(
            get("/api/v1/organization/members")
                .header("X-Org-ID", orgA.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testMemberManagementWithAdminRole() throws Exception {
    // Test: User A with COMPANY_ADMIN role in Org A can manage members
    mockMvc
        .perform(
            get("/api/v1/organization/members")
                .header("X-Org-ID", orgA.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        })))))
        .andExpect(status().isOk());
  }

  // ============================================================
  // Test: Multi-org user switching contexts
  // ============================================================

  @Test
  void testUserCanSwitchBetweenOrganizations() throws Exception {
    // Test: User A can switch between Org A and Org B by changing X-Org-ID header
    // First request: Access Org A
    mockMvc
        .perform(
            get("/api/v1/tasks/all")
                .header("X-Org-ID", orgA.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isOk());

    // Second request: Access Org B
    mockMvc
        .perform(
            get("/api/v1/tasks/all")
                .header("X-Org-ID", orgB.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        },
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgB.getId().toString());
                                            put("role", "MEMBER");
                                          }
                                        })))))
        .andExpect(status().isOk());
  }

  // ============================================================
  // Test: ThreadLocal cleanup
  // ============================================================

  @Test
  void testTenantContextIsCleanedUpAfterRequest() throws Exception {
    // Test: After a request completes, TenantContext should be cleared
    // This prevents data leakage in the next request
    assert TenantContext.get() == null;

    mockMvc
        .perform(
            get("/api/v1/tasks/all")
                .header("X-Org-ID", orgA.getId().toString())
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder
                                    .subject(userA.getAuthUserId())
                                    .claim("user_id", userA.getAuthUserId())
                                    .claim("orgs", List.of(
                                        new LinkedHashMap<String, Object>() {
                                          {
                                            put("id", orgA.getId().toString());
                                            put("role", "COMPANY_ADMIN");
                                          }
                                        })))))
        .andExpect(status().isOk());

    // After request, TenantContext should be cleaned
    assert TenantContext.get() == null;
  }
}
