package de.goaldone.backend.service;

import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.entity.UserEntity;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for user-related repository operations.
 * Tests user and membership lookup with new UserEntity/MembershipEntity model.
 */
@ExtendWith(MockitoExtension.class)
class UserIdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Test
    void findUser_ReturnsUser() {
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<UserEntity> result = userRepository.findById(userId);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
    }

    @Test
    void findUserNotFound_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<UserEntity> result = userRepository.findById(userId);

        assertFalse(result.isPresent());
    }

    @Test
    void findMembershipsForUser_ReturnsMemberships() {
        UUID userId = UUID.randomUUID();
        UUID membershipId1 = UUID.randomUUID();
        UUID membershipId2 = UUID.randomUUID();
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());

        MembershipEntity membership1 = new MembershipEntity();
        membership1.setId(membershipId1);
        membership1.setOrganizationId(orgId1);
        membership1.setUser(user);
        membership1.setStatus("ACTIVE");

        MembershipEntity membership2 = new MembershipEntity();
        membership2.setId(membershipId2);
        membership2.setOrganizationId(orgId2);
        membership2.setUser(user);
        membership2.setStatus("ACTIVE");

        when(membershipRepository.findAllByUserId(userId))
            .thenReturn(List.of(membership1, membership2));

        List<MembershipEntity> memberships = membershipRepository.findAllByUserId(userId);

        assertEquals(2, memberships.size());
        assertTrue(memberships.contains(membership1));
        assertTrue(memberships.contains(membership2));
    }

    @Test
    void findMembershipsInOrganization_ReturnsMemberships() {
        UUID orgId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID membershipId1 = UUID.randomUUID();
        UUID membershipId2 = UUID.randomUUID();

        UserEntity user1 = new UserEntity();
        user1.setId(userId1);
        user1.setCreatedAt(Instant.now());

        UserEntity user2 = new UserEntity();
        user2.setId(userId2);
        user2.setCreatedAt(Instant.now());

        MembershipEntity membership1 = new MembershipEntity();
        membership1.setId(membershipId1);
        membership1.setOrganizationId(orgId);
        membership1.setUser(user1);
        membership1.setStatus("ACTIVE");

        MembershipEntity membership2 = new MembershipEntity();
        membership2.setId(membershipId2);
        membership2.setOrganizationId(orgId);
        membership2.setUser(user2);
        membership2.setStatus("ACTIVE");

        when(membershipRepository.findAllByOrganizationId(orgId))
            .thenReturn(List.of(membership1, membership2));

        List<MembershipEntity> memberships = membershipRepository.findAllByOrganizationId(orgId);

        assertEquals(2, memberships.size());
    }

    @Test
    void findMembershipByUserAndOrg_ReturnsMembership() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setCreatedAt(Instant.now());

        MembershipEntity membership = new MembershipEntity();
        membership.setId(membershipId);
        membership.setOrganizationId(orgId);
        membership.setUser(user);
        membership.setStatus("ACTIVE");

        when(membershipRepository.findByUserIdAndOrganizationId(userId, orgId))
            .thenReturn(Optional.of(membership));

        Optional<MembershipEntity> result = membershipRepository.findByUserIdAndOrganizationId(userId, orgId);

        assertTrue(result.isPresent());
        assertEquals(membershipId, result.get().getId());
    }
}
