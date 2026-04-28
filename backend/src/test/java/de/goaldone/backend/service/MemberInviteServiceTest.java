package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.exception.UserAlreadyActiveException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.InviteMemberRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberInviteServiceTest {

    @Mock
    private ZitadelManagementClient zitadelManagementClient;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;
    @Mock
    private Jwt jwt;

    @InjectMocks
    private MemberInviteService memberInviteService;

    private final UUID orgId = UUID.randomUUID();
    private final String callerSub = "caller-sub";
    private final String projectId = "project-id";
    private final String mainOrgId = "main-org-id";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        ReflectionTestUtils.setField(memberInviteService, "goaldoneProjectId", projectId);
        ReflectionTestUtils.setField(memberInviteService, "mainOrgId", mainOrgId);
    }

    @Test
    void inviteMember_Success() {
        // Arrange
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("new@example.com");
        request.setFirstName("Max");
        request.setLastName("Mustermann");
        request.setRole(MemberRole.USER);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity organization = new OrganizationEntity();
        organization.setZitadelOrgId("customer-org-id");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        when(zitadelManagementClient.emailExists(request.getEmail())).thenReturn(false);
        when(zitadelManagementClient.addHumanUser(any(), any(), any(), any())).thenReturn("new-user-id");

        // Act
        memberInviteService.inviteMember(orgId, request);

        // Assert
        verify(zitadelManagementClient).addHumanUser("customer-org-id", "new@example.com", "Max", "Mustermann");
        verify(zitadelManagementClient).addUserGrant("new-user-id", mainOrgId, projectId, "USER");
        verify(zitadelManagementClient).createInviteCode("new-user-id");
    }

    @Test
    void inviteMember_EmailAlreadyExists() {
        // Arrange
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("existing@example.com");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(zitadelManagementClient.emailExists(request.getEmail())).thenReturn(true);

        // Act & Assert
        assertThrows(EmailAlreadyInUseException.class, () -> memberInviteService.inviteMember(orgId, request));
    }

    @Test
    void inviteMember_CompensationOnFailure() {
        // Arrange
        InviteMemberRequest request = new InviteMemberRequest();
        request.setEmail("new@example.com");
        request.setFirstName("Max");
        request.setLastName("Mustermann");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity organization = new OrganizationEntity();
        organization.setZitadelOrgId("zitadel-org-id");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(organization));

        when(zitadelManagementClient.addHumanUser(any(), any(), any(), any())).thenReturn("new-user-id");
        doThrow(new RuntimeException("API Error")).when(zitadelManagementClient).addUserGrant(any(), any(), any(), any());

        // Act & Assert
        assertThrows(ZitadelApiException.class, () -> memberInviteService.inviteMember(orgId, request));
        verify(zitadelManagementClient).deleteUser("new-user-id");
    }

    @Test
    void inviteMember_WrongOrganization() {
        // Arrange
        UUID otherOrgId = UUID.randomUUID();
        InviteMemberRequest request = new InviteMemberRequest();

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(otherOrgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        // Act & Assert
        assertThrows(NotMemberOfOrganizationException.class, () -> memberInviteService.inviteMember(orgId, request));
    }

    @Test
    void reinviteMember_Success() throws Exception {
        // Arrange
        String zitadelUserId = "user-id";
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNode = mapper.readTree("{\"state\": \"USER_STATE_INITIAL\"}");
        when(zitadelManagementClient.getUser(zitadelUserId)).thenReturn(Optional.of(userNode));

        // Act
        memberInviteService.reinviteMember(orgId, zitadelUserId);

        // Assert
        verify(zitadelManagementClient).createInviteCode(zitadelUserId);
    }

    @Test
    void reinviteMember_UserAlreadyActive() throws Exception {
        // Arrange
        String zitadelUserId = "user-id";
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(callerSub);

        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode userNode = mapper.readTree("{\"state\": \"USER_STATE_ACTIVE\"}");
        when(zitadelManagementClient.getUser(zitadelUserId)).thenReturn(Optional.of(userNode));

        // Act & Assert
        assertThrows(UserAlreadyActiveException.class, () -> memberInviteService.reinviteMember(orgId, zitadelUserId));
    }
}
