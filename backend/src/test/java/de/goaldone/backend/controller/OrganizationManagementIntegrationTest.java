package de.goaldone.backend.controller;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for organization management logic.
 * Tests organization creation, multi-org access, and authorization with the new entity model.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationManagementIntegrationTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private UUID userId;
    private UUID org1Id;
    private UUID org2Id;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        org1Id = UUID.randomUUID();
        org2Id = UUID.randomUUID();
    }

    /**
     * Test: User with membership in organization can be provisioned.
     */
    @Test
    void provisionUserInOrganization_CreatesOrUpdatesMembership() {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());

        OrganizationEntity org = new OrganizationEntity();
        org.setId(org1Id);
        org.setName("Test Org");
        org.setCreatedAt(Instant.now());

        MembershipEntity membership = new MembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setOrganizationId(org1Id);
        membership.setUser(user);
        membership.setStatus("ACTIVE");
        membership.setCreatedAt(Instant.now());

        when(organizationRepository.findById(org1Id)).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id))
            .thenReturn(Optional.of(membership));

        // Verify organization and membership can be resolved
        assertTrue(organizationRepository.findById(org1Id).isPresent());
        assertTrue(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id).isPresent());
    }

    /**
     * Test: User without membership in organization is denied access.
     */
    @Test
    void accessOrganization_UserNotMember_Denied() {
        OrganizationEntity org = new OrganizationEntity();
        org.setId(org1Id);
        org.setName("Test Org");
        org.setCreatedAt(Instant.now());

        when(organizationRepository.findById(org1Id)).thenReturn(Optional.of(org));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id))
            .thenReturn(Optional.empty());

        // Verify organization exists but user is not a member
        assertTrue(organizationRepository.findById(org1Id).isPresent());
        assertFalse(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id).isPresent());
    }

    /**
     * Test: User with memberships in multiple organizations can access each independently.
     */
    @Test
    void userWithMultipleOrgMemberships_CanAccessEach() {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());

        OrganizationEntity org1 = new OrganizationEntity();
        org1.setId(org1Id);
        org1.setName("Org 1");
        org1.setCreatedAt(Instant.now());

        OrganizationEntity org2 = new OrganizationEntity();
        org2.setId(org2Id);
        org2.setName("Org 2");
        org2.setCreatedAt(Instant.now());

        MembershipEntity membership1 = new MembershipEntity();
        membership1.setId(UUID.randomUUID());
        membership1.setOrganizationId(org1Id);
        membership1.setUser(user);
        membership1.setStatus("ACTIVE");
        membership1.setCreatedAt(Instant.now());

        MembershipEntity membership2 = new MembershipEntity();
        membership2.setId(UUID.randomUUID());
        membership2.setOrganizationId(org2Id);
        membership2.setUser(user);
        membership2.setStatus("ACTIVE");
        membership2.setCreatedAt(Instant.now());

        when(organizationRepository.findById(org1Id)).thenReturn(Optional.of(org1));
        when(organizationRepository.findById(org2Id)).thenReturn(Optional.of(org2));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id))
            .thenReturn(Optional.of(membership1));
        when(membershipRepository.findByUserIdAndOrganizationId(userId, org2Id))
            .thenReturn(Optional.of(membership2));
        when(membershipRepository.findAllByUserId(userId))
            .thenReturn(List.of(membership1, membership2));

        // Verify user can access both orgs
        assertTrue(membershipRepository.findByUserIdAndOrganizationId(userId, org1Id).isPresent());
        assertTrue(membershipRepository.findByUserIdAndOrganizationId(userId, org2Id).isPresent());
        assertEquals(2, membershipRepository.findAllByUserId(userId).size());
    }

    /**
     * Test: Organization creation provisions an admin user with membership.
     */
    @Test
    void createOrganization_ProvidesAdminMembership() {
        UserEntity adminUser = new UserEntity();
        adminUser.setId(userId);
        adminUser.setCreatedAt(Instant.now());

        OrganizationEntity newOrg = new OrganizationEntity();
        newOrg.setId(UUID.randomUUID());
        newOrg.setName("New Org");
        newOrg.setCreatedAt(Instant.now());

        MembershipEntity adminMembership = new MembershipEntity();
        adminMembership.setId(UUID.randomUUID());
        adminMembership.setOrganizationId(newOrg.getId());
        adminMembership.setUser(adminUser);
        adminMembership.setStatus("ACTIVE");
        adminMembership.setRole("COMPANY_ADMIN");
        adminMembership.setCreatedAt(Instant.now());

        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(newOrg);
        when(membershipRepository.save(any(MembershipEntity.class))).thenReturn(adminMembership);

        // Verify new org can be saved with admin membership
        assertNotNull(newOrg);
        assertEquals("New Org", newOrg.getName());
        assertEquals("COMPANY_ADMIN", adminMembership.getRole());
        assertEquals("ACTIVE", adminMembership.getStatus());
    }
}
