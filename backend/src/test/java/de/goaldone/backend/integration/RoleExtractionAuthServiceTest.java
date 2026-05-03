package de.goaldone.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for role extraction from auth-service JWT.
 * Verifies that role authorities are correctly extracted and parsed.
 */
class RoleExtractionAuthServiceTest {

    @Test
    void testRoleExtraction_SingleRole() {
        Jwt jwt = buildJwtWithAuthorities(List.of("ROLE_COMPANY_ADMIN"));

        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertEquals(1, authorities.size());
        assertEquals("ROLE_COMPANY_ADMIN", authorities.get(0));
    }

    @Test
    void testRoleExtraction_MultipleRoles() {
        Jwt jwt = buildJwtWithAuthorities(List.of("ROLE_COMPANY_ADMIN", "ROLE_COMPANY_MEMBER"));

        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains("ROLE_COMPANY_ADMIN"));
        assertTrue(authorities.contains("ROLE_COMPANY_MEMBER"));
    }

    @Test
    void testRoleExtraction_NoAuthorities() {
        Jwt jwt = buildJwtWithAuthorities(List.of());

        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertNotNull(authorities);
        assertEquals(0, authorities.size());
    }

    @Test
    void testRoleExtraction_NullAuthorities() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("user-id")
            .claim("user_id", "user-id")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claims(c -> c.putAll(claims.getClaims()))
            .build();

        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertNull(authorities);
    }

    @Test
    void testRoleExtraction_InvalidFormat() {
        // Authorities should be a list of strings, test with invalid format
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("user-id")
            .claim("user_id", "user-id")
            .claim("authorities", "invalid-string")  // Should be list
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claims(c -> c.putAll(claims.getClaims()))
            .build();

        Object authorities = jwt.getClaim("authorities");
        assertNotNull(authorities);
    }

    @Test
    void testRoleExtraction_RbacValidation() {
        Jwt jwt = buildJwtWithAuthorities(List.of("ROLE_COMPANY_ADMIN"));

        List<String> authorities = (List<String>) jwt.getClaim("authorities");

        // Simulate RBAC check: user with ROLE_COMPANY_ADMIN should have access
        assertTrue(authorities.contains("ROLE_COMPANY_ADMIN"));
    }

    @Test
    void testRoleExtraction_RbacDenial() {
        Jwt jwt = buildJwtWithAuthorities(List.of("ROLE_COMPANY_MEMBER"));

        List<String> authorities = (List<String>) jwt.getClaim("authorities");

        // Simulate RBAC check: user without ROLE_COMPANY_ADMIN should be denied
        assertFalse(authorities.contains("ROLE_COMPANY_ADMIN"));
    }

    @Test
    void testRoleExtraction_MixedRoles() {
        List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        Jwt jwt = buildJwtWithAuthorities(roles);

        List<String> authorities = (List<String>) jwt.getClaim("authorities");
        assertEquals(3, authorities.size());
        for (String role : roles) {
            assertTrue(authorities.contains(role), "Role " + role + " not found");
        }
    }

    @Test
    void testRoleExtraction_RolePrefix() {
        Jwt jwt = buildJwtWithAuthorities(List.of("ROLE_COMPANY_ADMIN", "ROLE_COMPANY_MEMBER"));

        List<String> authorities = (List<String>) jwt.getClaim("authorities");

        // All roles should start with ROLE_ prefix
        for (String role : authorities) {
            assertTrue(role.startsWith("ROLE_"), "Role " + role + " does not start with ROLE_");
        }
    }

    private Jwt buildJwtWithAuthorities(List<String> authorities) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject("user-id")
            .claim("user_id", "user-id")
            .claim("authorities", authorities)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claims(c -> c.putAll(claims.getClaims()))
            .build();
    }
}
