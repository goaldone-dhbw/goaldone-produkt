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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SuperAdmin entity persistence.
 * Tests creation and deletion of super admin memberships.
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class SuperAdminIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void testCreateSuperAdmin_Success() {
        // Create user, org, and super admin membership
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Goaldone");
        org.setCreatedAt(Instant.now());
        organizationRepository.save(org);

        MembershipEntity adminMembership = new MembershipEntity();
        adminMembership.setId(membershipId);
        adminMembership.setOrganizationId(orgId);
        adminMembership.setUser(user);
        adminMembership.setStatus("ACTIVE");
        adminMembership.setRole("SUPER_ADMIN");
        adminMembership.setCreatedAt(Instant.now());
        membershipRepository.save(adminMembership);

        // Verify
        assertTrue(membershipRepository.existsById(membershipId));
        var found = membershipRepository.findById(membershipId);
        assertTrue(found.isPresent());
        assertEquals("SUPER_ADMIN", found.get().getRole());
    }

    @Test
    void testDeleteSuperAdmin_Success() {
        // Create super admin
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Goaldone");
        org.setCreatedAt(Instant.now());
        organizationRepository.save(org);

        MembershipEntity adminMembership = new MembershipEntity();
        adminMembership.setId(membershipId);
        adminMembership.setOrganizationId(orgId);
        adminMembership.setUser(user);
        adminMembership.setStatus("ACTIVE");
        adminMembership.setRole("SUPER_ADMIN");
        adminMembership.setCreatedAt(Instant.now());
        membershipRepository.save(adminMembership);

        // Delete
        membershipRepository.deleteById(membershipId);

        // Verify deletion
        assertFalse(membershipRepository.existsById(membershipId));
    }

    @Test
    void testListSuperAdmins_Success() {
        // Create multiple super admins
        for (int i = 0; i < 2; i++) {
            UUID userId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();

            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setCreatedAt(Instant.now());
            userRepository.save(user);

            OrganizationEntity org = new OrganizationEntity();
            org.setId(orgId);
            org.setName("Org " + i);
            org.setCreatedAt(Instant.now());
            organizationRepository.save(org);

            MembershipEntity membership = new MembershipEntity();
            membership.setId(membershipId);
            membership.setOrganizationId(orgId);
            membership.setUser(user);
            membership.setStatus("ACTIVE");
            membership.setRole("SUPER_ADMIN");
            membership.setCreatedAt(Instant.now());
            membershipRepository.save(membership);
        }

        // Verify count
        long count = membershipRepository.count();
        assertEquals(2, count);
    }
}
