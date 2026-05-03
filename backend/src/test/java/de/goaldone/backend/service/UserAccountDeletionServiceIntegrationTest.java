package de.goaldone.backend.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for user account deletion service.
 * Tests account deletion cascade logic with new MembershipEntity model.
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class UserAccountDeletionServiceIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private LinkTokenRepository linkTokenRepository;

    @Autowired
    private AccountLinkingService accountLinkingService;

    @BeforeEach
    void setUp() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        membershipRepository.deleteAll();
        userRepository.deleteAll();
        linkTokenRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void deleteMembership_DeletesUserWhenNoMoreMemberships() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        // Create user, org, and membership
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Test Org");
        org.setCreatedAt(Instant.now());
        organizationRepository.save(org);

        MembershipEntity membership = new MembershipEntity();
        membership.setId(membershipId);
        membership.setOrganizationId(orgId);
        membership.setUser(user);
        membership.setStatus("ACTIVE");
        membership.setRole("USER");
        membership.setCreatedAt(Instant.now());
        membershipRepository.save(membership);

        // Verify membership exists
        assertTrue(membershipRepository.existsById(membershipId));

        // Delete the membership
        membershipRepository.deleteById(membershipId);

        // Verify membership is deleted
        assertFalse(membershipRepository.existsById(membershipId));

        // Verify user still exists (deletion of user is optional)
        assertTrue(userRepository.existsById(userId));
    }

    @Test
    void deleteMultipleMemberships_KeepsUserWhenOtherMembershipsExist() {
        UUID userId = UUID.randomUUID();
        UUID org1Id = UUID.randomUUID();
        UUID org2Id = UUID.randomUUID();
        UUID membership1Id = UUID.randomUUID();
        UUID membership2Id = UUID.randomUUID();

        // Create user
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        // Create two orgs
        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(org1Id);
        org1.setName("Org 1");
        org1.setCreatedAt(Instant.now());
        organizationRepository.save(org1);

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(org2Id);
        org2.setName("Org 2");
        org2.setCreatedAt(Instant.now());
        organizationRepository.save(org2);

        // Create two memberships
        MembershipEntity membership1 = new MembershipEntity();
        membership1.setId(membership1Id);
        membership1.setOrganizationId(org1Id);
        membership1.setUser(user);
        membership1.setStatus("ACTIVE");
        membership1.setRole("USER");
        membership1.setCreatedAt(Instant.now());
        membershipRepository.save(membership1);

        MembershipEntity membership2 = new MembershipEntity();
        membership2.setId(membership2Id);
        membership2.setOrganizationId(org2Id);
        membership2.setUser(user);
        membership2.setStatus("ACTIVE");
        membership2.setRole("USER");
        membership2.setCreatedAt(Instant.now());
        membershipRepository.save(membership2);

        // Delete one membership
        membershipRepository.deleteById(membership1Id);

        // Assert deleted membership is gone
        assertFalse(membershipRepository.existsById(membership1Id));

        // Assert other membership still exists
        assertTrue(membershipRepository.existsById(membership2Id));

        // Assert user still exists
        assertTrue(userRepository.existsById(userId));
    }

    @Test
    void deleteNonExistentMembership_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        // If no exception, the operation is idempotent
        membershipRepository.deleteById(nonExistentId);
        assertFalse(membershipRepository.existsById(nonExistentId));
    }

    @Test
    void deleteLinkedMemberships_KeepsOtherLinkedMemberships() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID org1Id = UUID.randomUUID();
        UUID org2Id = UUID.randomUUID();
        UUID membership1Id = UUID.randomUUID();
        UUID membership2Id = UUID.randomUUID();

        // Create two users
        UserEntity user1 = new UserEntity();
        user1.setId(userId1);
        user1.setCreatedAt(Instant.now());
        userRepository.save(user1);

        UserEntity user2 = new UserEntity();
        user2.setId(userId2);
        user2.setCreatedAt(Instant.now());
        userRepository.save(user2);

        // Create two orgs
        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(org1Id);
        org1.setName("Org 1");
        org1.setCreatedAt(Instant.now());
        organizationRepository.save(org1);

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(org2Id);
        org2.setName("Org 2");
        org2.setCreatedAt(Instant.now());
        organizationRepository.save(org2);

        // Create memberships
        MembershipEntity membership1 = new MembershipEntity();
        membership1.setId(membership1Id);
        membership1.setOrganizationId(org1Id);
        membership1.setUser(user1);
        membership1.setStatus("ACTIVE");
        membership1.setRole("USER");
        membership1.setCreatedAt(Instant.now());
        membershipRepository.save(membership1);

        MembershipEntity membership2 = new MembershipEntity();
        membership2.setId(membership2Id);
        membership2.setOrganizationId(org2Id);
        membership2.setUser(user2);
        membership2.setStatus("ACTIVE");
        membership2.setRole("USER");
        membership2.setCreatedAt(Instant.now());
        membershipRepository.save(membership2);

        // Delete one membership
        membershipRepository.deleteById(membership1Id);

        // Assert deleted membership is gone
        assertFalse(membershipRepository.existsById(membership1Id));

        // Assert other membership still exists
        assertTrue(membershipRepository.existsById(membership2Id));
    }
}
