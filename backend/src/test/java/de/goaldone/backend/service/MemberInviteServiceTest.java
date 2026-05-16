package de.goaldone.backend.service;

import com.zitadel.model.UserServiceUser;
import com.zitadel.model.UserServiceUserState;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.UserAlreadyActiveException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberInviteServiceTest {

    @Mock
    private ZitadelManagementClient zitadelManagementClient;
    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private MemberInviteService memberInviteService;

    private final UUID orgId = UUID.randomUUID();
    private final String projectId = "project-id";
    private final String mainOrgId = "main-org-id";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(memberInviteService, "goaldoneProjectId", projectId);
        ReflectionTestUtils.setField(memberInviteService, "mainOrgId", mainOrgId);
    }

    @Test
    void inviteMember_Success() {
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("new@example.com");
        request.setFirstName("Max");
        request.setLastName("Mustermann");
        request.setRole(MemberRole.USER);

        OrganizationEntity organization = new OrganizationEntity();
        organization.setZitadelOrgId("customer-org-id");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        when(zitadelManagementClient.emailExists(request.getEmail())).thenReturn(false);
        when(zitadelManagementClient.addHumanUser(any(), any(), any(), any())).thenReturn("new-user-id");

        memberInviteService.inviteMember(orgId, request);

        verify(zitadelManagementClient).addHumanUser("customer-org-id", "new@example.com", "Max", "Mustermann");
        verify(zitadelManagementClient).addUserGrant("new-user-id", mainOrgId, projectId, "USER");
        verify(zitadelManagementClient).createInviteCode("new-user-id");
    }

    @Test
    void inviteMember_EmailAlreadyExists() {
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("existing@example.com");

        when(zitadelManagementClient.emailExists(request.getEmail())).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> memberInviteService.inviteMember(orgId, request));
    }

    @Test
    void inviteMember_CompensationOnFailure() {
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("new@example.com");
        request.setFirstName("Max");
        request.setLastName("Mustermann");

        OrganizationEntity organization = new OrganizationEntity();
        organization.setZitadelOrgId("zitadel-org-id");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        when(zitadelManagementClient.addHumanUser(any(), any(), any(), any())).thenReturn("new-user-id");
        doThrow(new RuntimeException("API Error")).when(zitadelManagementClient).addUserGrant(any(), any(), any(), any());

        assertThrows(ZitadelApiException.class, () -> memberInviteService.inviteMember(orgId, request));
        verify(zitadelManagementClient).deleteUser("new-user-id");
    }

    @Test
    void reinviteMember_Success() throws Exception {
        String zitadelUserId = "user-id";

        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getState()).thenReturn(UserServiceUserState.USER_STATE_INITIAL);
        when(zitadelManagementClient.getUser(zitadelUserId)).thenReturn(Optional.of(user));

        memberInviteService.reinviteMember(orgId, zitadelUserId);

        verify(zitadelManagementClient).createInviteCode(zitadelUserId);
    }

    @Test
    void reinviteMember_UserAlreadyActive() throws Exception {
        String zitadelUserId = "user-id";

        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getState()).thenReturn(UserServiceUserState.USER_STATE_ACTIVE);
        when(zitadelManagementClient.getUser(zitadelUserId)).thenReturn(Optional.of(user));

        assertThrows(UserAlreadyActiveException.class, () -> memberInviteService.reinviteMember(orgId, zitadelUserId));
    }
}
