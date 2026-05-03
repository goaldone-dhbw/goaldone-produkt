package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.model.MemberStatus;
import de.goaldone.backend.repository.MembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberInviteServiceTest {

    @Mock private AuthServiceManagementClient authServiceClient;
    @Mock private MembershipRepository membershipRepository;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;
    @Mock private Jwt jwt;

    private MemberInviteService service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID INVITER_ID = UUID.randomUUID();
    private static final UUID INVITATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MemberInviteService(authServiceClient, membershipRepository);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(INVITER_ID.toString());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inviteMember_createsEagerPendingMembership() {
        InviteMemberRequest req = new InviteMemberRequest().email("new@example.com").role(MemberRole.USER);
        when(authServiceClient.createInvitation(eq(ORG_ID), eq("new@example.com"), eq(INVITER_ID), eq(MemberRole.USER)))
                .thenReturn(INVITATION_ID);

        service.inviteMember(ORG_ID, req);

        ArgumentCaptor<MembershipEntity> captor = ArgumentCaptor.forClass(MembershipEntity.class);
        verify(membershipRepository).save(captor.capture());
        MembershipEntity saved = captor.getValue();

        assertEquals(MemberStatus.INVITED.getValue(), saved.getStatus());
        assertEquals(INVITATION_ID, saved.getInvitationId());
        assertEquals("new@example.com", saved.getEmail());
    }

    @Test
    void reinviteMember_callsCancelInvitation_andUpdatesInvitationId() {
        UUID membershipId = UUID.randomUUID();
        UUID oldInvitationId = UUID.randomUUID();
        UUID newInvitationId = UUID.randomUUID();

        MembershipEntity existing = new MembershipEntity();
        existing.setId(membershipId);
        existing.setOrganizationId(ORG_ID);
        existing.setStatus(MemberStatus.INVITED.getValue());
        existing.setInvitationId(oldInvitationId);
        existing.setEmail("user@example.com");
        existing.setRole(MemberRole.USER.getValue());
        existing.setCreatedAt(Instant.now());

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(existing));
        when(authServiceClient.createInvitation(eq(ORG_ID), eq("user@example.com"), eq(INVITER_ID), eq(MemberRole.USER)))
                .thenReturn(newInvitationId);

        service.reinviteMember(ORG_ID, membershipId);

        verify(authServiceClient).cancelInvitation(oldInvitationId);
        verify(membershipRepository).save(existing);
        assertEquals(newInvitationId, existing.getInvitationId());
    }

    @Test
    void reinviteMember_updatesInvitationId_whenNoPreviousInvitation() {
        UUID membershipId = UUID.randomUUID();
        UUID newInvitationId = UUID.randomUUID();

        MembershipEntity existing = new MembershipEntity();
        existing.setId(membershipId);
        existing.setOrganizationId(ORG_ID);
        existing.setStatus(MemberStatus.INVITED.getValue());
        existing.setInvitationId(null);
        existing.setEmail("user2@example.com");
        existing.setRole(MemberRole.USER.getValue());
        existing.setCreatedAt(Instant.now());

        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(existing));
        when(authServiceClient.createInvitation(any(), any(), any(), any())).thenReturn(newInvitationId);

        service.reinviteMember(ORG_ID, membershipId);

        verify(authServiceClient, never()).cancelInvitation(any());
        assertEquals(newInvitationId, existing.getInvitationId());
    }
}
