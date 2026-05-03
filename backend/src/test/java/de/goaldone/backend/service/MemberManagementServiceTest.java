package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.client.dto.AuthMemberResponse;
import de.goaldone.backend.entity.MembershipEntity;
import de.goaldone.backend.model.ChangeRoleRequest;
import de.goaldone.backend.model.MemberListResponse;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.repository.MembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberManagementServiceTest {

    @Mock private AuthServiceManagementClient authServiceClient;
    @Mock private MembershipRepository membershipRepository;
    @Mock private UserService userService;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;
    @Mock private Jwt jwt;

    private MemberManagementService service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MemberManagementService(authServiceClient, membershipRepository, userService);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(CALLER_ID.toString());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMembers_returnsActiveAndInvitedMembers() {
        AuthMemberResponse active = new AuthMemberResponse();
        active.setUserId(UUID.randomUUID());
        active.setEmail("active@example.com");
        active.setRole("USER");
        active.setStatus("ACTIVE");

        AuthMemberResponse invited = new AuthMemberResponse();
        invited.setUserId(UUID.randomUUID());
        invited.setEmail("invited@example.com");
        invited.setRole("USER");
        invited.setStatus("INVITED");

        doNothing().when(userService).validateMembership(ORG_ID);
        when(authServiceClient.getMembers(ORG_ID)).thenReturn(List.of(active, invited));

        MemberListResponse result = service.listMembers(ORG_ID);

        assertNotNull(result);
        assertEquals(2, result.getMembers().size());
        verify(authServiceClient).getMembers(ORG_ID);
    }

    @Test
    void changeMemberRole_success() {
        ChangeRoleRequest req = new ChangeRoleRequest().role(MemberRole.COMPANY_ADMIN);
        doNothing().when(userService).validateMembership(ORG_ID);

        assertDoesNotThrow(() -> service.changeMemberRole(ORG_ID, USER_ID, req));

        verify(authServiceClient).updateMembershipRole(USER_ID, ORG_ID, MemberRole.COMPANY_ADMIN);
    }

    @Test
    void changeMemberRole_lastAdmin_throws409() {
        ChangeRoleRequest req = new ChangeRoleRequest().role(MemberRole.USER);
        doNothing().when(userService).validateMembership(ORG_ID);
        doThrow(new AuthServiceManagementException("last admin", 409))
                .when(authServiceClient).updateMembershipRole(any(), any(), any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.changeMemberRole(ORG_ID, USER_ID, req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void removeMember_success() {
        doNothing().when(userService).validateMembership(ORG_ID);
        MembershipEntity membership = new MembershipEntity();
        membership.setId(UUID.randomUUID());
        when(membershipRepository.findByUserIdAndOrganizationId(USER_ID, ORG_ID))
                .thenReturn(Optional.of(membership));

        assertDoesNotThrow(() -> service.removeMember(ORG_ID, USER_ID));

        verify(authServiceClient).deleteMembership(USER_ID, ORG_ID);
        verify(membershipRepository).delete(membership);
    }

    @Test
    void removeMember_lastAdmin_throws409() {
        doNothing().when(userService).validateMembership(ORG_ID);
        doThrow(new AuthServiceManagementException("last admin", 409))
                .when(authServiceClient).deleteMembership(any(), any());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.removeMember(ORG_ID, USER_ID));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }
}
