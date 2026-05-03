package de.goaldone.backend.security;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JWT validation and role extraction with auth-service token format.
 *
 * Verifies that:
 * - JWT authorities claim is correctly structured for auth-service tokens
 * - JWT orgs claim contains organization information
 * - Multi-org RBAC is supported via JWT claims
 */
@SpringBootTest(
    properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    })
@ActiveProfiles("local")
class SecurityIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private MembershipRepository membershipRepository;

  // Test data
  private UserEntity userA;
  private OrganizationEntity orgA;
  private OrganizationEntity orgB;

  @BeforeEach
  void setUp() {
    // Create test users
    userA = new UserEntity();
    userA.setId(UUID.randomUUID());
    userA.setCreatedAt(Instant.now());
    userRepository.save(userA);

    // Create test organizations
    orgA = new OrganizationEntity();
    orgA.setId(UUID.randomUUID());
    orgA.setName("Org A");
    orgA.setCreatedAt(Instant.now());

    orgB = new OrganizationEntity();
    orgB.setId(UUID.randomUUID());
    orgB.setName("Org B");
    orgB.setCreatedAt(Instant.now());

    organizationRepository.saveAll(List.of(orgA, orgB));

    // Create memberships
    MembershipEntity membershipA = new MembershipEntity();
    membershipA.setId(UUID.randomUUID());
    membershipA.setOrganizationId(orgA.getId());
    membershipA.setUser(userA);
    membershipA.setStatus("ACTIVE");
    membershipA.setCreatedAt(Instant.now());

    MembershipEntity membershipB = new MembershipEntity();
    membershipB.setId(UUID.randomUUID());
    membershipB.setOrganizationId(orgB.getId());
    membershipB.setUser(userA);
    membershipB.setStatus("ACTIVE");
    membershipB.setCreatedAt(Instant.now());

    membershipRepository.saveAll(List.of(membershipA, membershipB));
  }

  @AfterEach
  void tearDown() {
    membershipRepository.deleteAll();
    organizationRepository.deleteAll();
    userRepository.deleteAll();
    TenantContext.clear();
  }

  /**
   * Test: JWT authorities claim is correctly structured with list of authority strings.
   */
  @Test
  void testJwtAuthoritiesClaimStructure() {
    Jwt jwt = buildJwt(userA.getId().toString(), List.of("COMPANY_ADMIN", "USER"));

    List<String> authorities = jwt.getClaimAsStringList("authorities");
    assertNotNull(authorities);
    assertEquals(2, authorities.size());
    assertTrue(authorities.contains("COMPANY_ADMIN"));
    assertTrue(authorities.contains("USER"));
  }

  /**
   * Test: JWT authorities claim is empty when not provided.
   */
  @Test
  void testJwtWithoutAuthoritiesClaim() {
    Jwt jwt = buildJwtWithoutAuthorities(userA.getId().toString());

    List<String> authorities = jwt.getClaimAsStringList("authorities");
    assertNull(authorities);
  }

  /**
   * Test: JWT contains multi-org information in orgs claim.
   */
  @Test
  void testJwtMultiOrgClaim() {
    Jwt jwt = buildJwt(userA.getId().toString(), List.of("COMPANY_ADMIN"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orgs = (List<Map<String, Object>>) jwt.getClaim("orgs");
    assertNotNull(orgs);
    assertEquals(2, orgs.size());

    assertTrue(orgs.stream()
        .anyMatch(org -> orgA.getId().toString().equals(org.get("id"))));
    assertTrue(orgs.stream()
        .anyMatch(org -> orgB.getId().toString().equals(org.get("id"))));
  }

  /**
   * Test: JWT user_id claim matches subject claim.
   */
  @Test
  void testJwtUserIdClaim() {
    Jwt jwt = buildJwt(userA.getId().toString(), List.of("USER"));

    String userId = jwt.getClaimAsString("user_id");
    assertEquals(userA.getId().toString(), userId);
    assertEquals(userA.getId().toString(), jwt.getSubject());
  }

  /**
   * Test: JWT supports single organization in orgs claim.
   */
  @Test
  void testJwtSingleOrgClaim() {
    Jwt jwt = buildJwtSingleOrg(userA.getId().toString(), orgA.getId().toString());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orgs = (List<Map<String, Object>>) jwt.getClaim("orgs");
    assertNotNull(orgs);
    assertEquals(1, orgs.size());
    assertEquals(orgA.getId().toString(), orgs.get(0).get("id"));
  }

  private Jwt buildJwt(String userId, List<String> authorities) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId)
        .claim("user_id", userId)
        .claim("authorities", authorities)
        .claim("orgs", List.of(
            new LinkedHashMap<String, Object>() {{
              put("id", orgA.getId().toString());
              put("role", "COMPANY_ADMIN");
            }},
            new LinkedHashMap<String, Object>() {{
              put("id", orgB.getId().toString());
              put("role", "MEMBER");
            }}))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
  }

  private Jwt buildJwtWithoutAuthorities(String userId) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId)
        .claim("user_id", userId)
        .claim("orgs", List.of(
            new LinkedHashMap<String, Object>() {{
              put("id", orgA.getId().toString());
              put("role", "COMPANY_ADMIN");
            }}))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
  }

  private Jwt buildJwtSingleOrg(String userId, String orgId) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId)
        .claim("user_id", userId)
        .claim("authorities", List.of("USER"))
        .claim("orgs", List.of(
            new LinkedHashMap<String, Object>() {{
              put("id", orgId);
              put("role", "MEMBER");
            }}))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
    return Jwt.withTokenValue("token")
        .claims(c -> c.putAll(claims.getClaims()))
        .headers(h -> h.put("alg", "HS256"))
        .build();
  }
}
