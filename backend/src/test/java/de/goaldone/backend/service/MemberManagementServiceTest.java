package de.goaldone.backend.service;

import com.zitadel.model.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import de.goaldone.backend.client.UserGrantDto;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.exception.NotMemberOfOrganizationException;
import de.goaldone.backend.model.ChangeRoleRequest;
import de.goaldone.backend.model.MemberListResponse;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.model.MemberStatus;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberManagementServiceTest {

    @Mock
    private ZitadelManagementClient zitadelManagementClient;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserAccountDeletionService userAccountDeletionService;

    @InjectMocks
    private MemberManagementService memberManagementService;

    private UUID orgId;
    private String callerSub = "caller-sub";

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        
        // Mock Security Context
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(callerSub);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Reflection to set @Value fields
        java.lang.reflect.Field projectField = MemberManagementService.class.getDeclaredField("goaldoneProjectId");
        projectField.setAccessible(true);
        projectField.set(memberManagementService, "project-id");

        java.lang.reflect.Field orgField = MemberManagementService.class.getDeclaredField("mainOrgId");
        orgField.setAccessible(true);
        orgField.set(memberManagementService, "main-org-id");
    }

    @Test
    void listMembers_MixedStatus_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity org = new OrganizationEntity();
        org.setZitadelOrgId("zitadel-org-id");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        AuthorizationServiceListAuthorizationsResponse grantsResponse = buildAuthorizationsResponse(
                List.of("user-1", "user-2"),
                List.of("USER", "COMPANY_ADMIN")
        );
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), anyString())).thenReturn(grantsResponse);

        UserServiceUser user1 = buildUser("user-1", "USER_STATE_ACTIVE", "user1@test.com", "User", "One", "2023-01-01T00:00:00Z");
        UserServiceUser user2 = buildUser("user-2", "USER_STATE_INITIAL", "user2@test.com", "User", "Two", "2023-01-02T00:00:00Z");
        when(zitadelManagementClient.listUsersByIds(anyList())).thenReturn(List.of(user1, user2));

        UserAccountEntity acc1 = new UserAccountEntity();
        acc1.setId(UUID.randomUUID());
        acc1.setZitadelSub("user-1");
        acc1.setOrganizationId(orgId);
        when(userAccountRepository.findAll()).thenReturn(List.of(acc1));

        // Act
        MemberListResponse response = memberManagementService.listMembers(orgId);

        // Assert
        assertThat(response.getMembers()).hasSize(2);
        
        var m1 = response.getMembers().stream().filter(m -> m.getZitadelUserId().equals("user-1")).findFirst().get();
        assertThat(m1.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(m1.getRole()).isEqualTo(MemberRole.USER);
        assertThat(m1.getAccountId()).isNotNull();

        var m2 = response.getMembers().stream().filter(m -> m.getZitadelUserId().equals("user-2")).findFirst().get();
        assertThat(m2.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(m2.getRole()).isEqualTo(MemberRole.COMPANY_ADMIN);
        assertThat(m2.getAccountId()).isNull();
    }

    @Test
    void changeMemberRole_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("USER"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(MemberRole.COMPANY_ADMIN);

        // Act
        memberManagementService.changeMemberRole(orgId, "target-user", request);

        // Assert
        verify(zitadelManagementClient).updateUserGrant(eq("grant-1"), anyString(), eq(List.of(MemberRole.COMPANY_ADMIN.getValue())));
    }

    @Test
    void changeMemberRole_LastAdmin_ThrowsConflict() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity org = new OrganizationEntity();
        org.setZitadelOrgId("user-org");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("COMPANY_ADMIN"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        AuthorizationServiceListAuthorizationsResponse allGrants = buildAuthorizationsResponse(
                List.of("target-user", "user-3"),
                List.of("COMPANY_ADMIN", "USER")
        );
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(allGrants);

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(MemberRole.USER);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, 
                () -> memberManagementService.changeMemberRole(orgId, "target-user", request));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).isEqualTo("LAST_ADMIN_CANNOT_BE_DEMOTED");
    }

    @Test
    void removeMember_ActiveUser_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("USER"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        UserAccountEntity targetAcc = new UserAccountEntity();
        UUID targetAccId = UUID.randomUUID();
        targetAcc.setId(targetAccId);
        targetAcc.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub("target-user")).thenReturn(Optional.of(targetAcc));

        // Act
        memberManagementService.removeMember(orgId, "target-user");

        // Assert
        verify(userAccountDeletionService).deleteUserAccount(targetAccId);
        verify(zitadelManagementClient, never()).deleteUser(anyString());
    }

    @Test
    void removeMember_InvitedUser_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("USER"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        when(userAccountRepository.findByZitadelSub("target-user")).thenReturn(Optional.empty());

        // Act
        memberManagementService.removeMember(orgId, "target-user");

        // Assert
        verify(zitadelManagementClient).deleteUser("target-user");
        verify(userAccountDeletionService, never()).deleteUserAccount(any());
    }

    @Test
    void removeMember_SelfRemove_ThrowsForbidden() {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memberManagementService.removeMember(orgId, callerSub));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getReason()).isEqualTo("CANNOT_REMOVE_SELF");
    }

    @Test
    void changeMemberRole_DemoteAdmin_MultipleAdmins_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity org = new OrganizationEntity();
        org.setZitadelOrgId("user-org");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("COMPANY_ADMIN"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        AuthorizationServiceListAuthorizationsResponse allGrants = buildAuthorizationsResponse(
                List.of("target-user", "other-admin"),
                List.of("COMPANY_ADMIN", "COMPANY_ADMIN")
        );
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(allGrants);

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(MemberRole.USER);

        // Act
        memberManagementService.changeMemberRole(orgId, "target-user", request);

        // Assert
        verify(zitadelManagementClient).updateUserGrant(eq("grant-1"), anyString(), eq(List.of(MemberRole.USER.getValue())));
    }

    @Test
    void changeMemberRole_SelfDemotion_MultipleAdmins_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity org = new OrganizationEntity();
        org.setZitadelOrgId("user-org");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("COMPANY_ADMIN"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq(callerSub)))
                .thenReturn(Optional.of(grant));

        AuthorizationServiceListAuthorizationsResponse allGrants = buildAuthorizationsResponse(
                List.of("caller-sub", "other-admin"),
                List.of("COMPANY_ADMIN", "COMPANY_ADMIN")
        );
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(allGrants);

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(MemberRole.USER);

        // Act
        memberManagementService.changeMemberRole(orgId, callerSub, request);

        // Assert
        verify(zitadelManagementClient).updateUserGrant(eq("grant-1"), anyString(), eq(List.of(MemberRole.USER.getValue())));
    }

    @Test
    void removeMember_LastAdmin_ThrowsConflict() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        OrganizationEntity org = new OrganizationEntity();
        org.setZitadelOrgId("user-org");
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("COMPANY_ADMIN"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        AuthorizationServiceListAuthorizationsResponse allGrants = buildAuthorizationsResponse(
                List.of("target-user"),
                List.of("COMPANY_ADMIN")
        );
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(allGrants);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memberManagementService.removeMember(orgId, "target-user"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getReason()).isEqualTo("LAST_ADMIN_CANNOT_BE_REMOVED");
        verify(zitadelManagementClient, never()).deleteUser(anyString());
    }

    @Test
    void listMembers_CallerNotInOrg_ThrowsForbidden() {
        // Arrange
        UUID differentOrgId = UUID.randomUUID();
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(differentOrgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        // Act & Assert
        assertThrows(NotMemberOfOrganizationException.class,
                () -> memberManagementService.listMembers(orgId));
    }

    @Test
    void changeMemberRole_RoleUnchanged_ThrowsBadRequest() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        UserGrantDto grant = new UserGrantDto("grant-1", List.of("USER"));
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(grant));

        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(MemberRole.USER);

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memberManagementService.changeMemberRole(orgId, "target-user", request));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).isEqualTo("ROLE_UNCHANGED");
    }

    @Test
    void removeMember_UserNotFoundInOrg_ThrowsNotFound() {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("unknown-user")))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memberManagementService.removeMember(orgId, "unknown-user"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(zitadelManagementClient, never()).deleteUser(anyString());
    }

    // Helper methods for building SDK objects using mocks
    private UserServiceUser buildUser(String userId, String state, String email, String firstName, String lastName, String creationDate) {
        UserServiceUserState userState = "USER_STATE_ACTIVE".equals(state)
                ? UserServiceUserState.USER_STATE_ACTIVE
                : UserServiceUserState.USER_STATE_INITIAL;

        UserServiceHumanEmail humanEmail = mock(UserServiceHumanEmail.class);
        when(humanEmail.getEmail()).thenReturn(email);

        UserServiceHumanProfile profile = mock(UserServiceHumanProfile.class);
        when(profile.getGivenName()).thenReturn(firstName);
        when(profile.getFamilyName()).thenReturn(lastName);

        UserServiceHumanUser human = mock(UserServiceHumanUser.class);
        when(human.getEmail()).thenReturn(humanEmail);
        when(human.getProfile()).thenReturn(profile);

        // SDK getCreationDate() returns OffsetDateTime
        OffsetDateTime creationDateODT = OffsetDateTime.parse(creationDate);

        UserServiceDetails details = mock(UserServiceDetails.class);
        when(details.getCreationDate()).thenReturn(creationDateODT);

        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getState()).thenReturn(userState);
        when(user.getHuman()).thenReturn(human);
        when(user.getDetails()).thenReturn(details);

        return user;
    }

    private AuthorizationServiceListAuthorizationsResponse buildAuthorizationsResponse(List<String> userIds, List<String> roleKeys) {
        List<AuthorizationServiceAuthorization> authorizations = new java.util.ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            String userId = userIds.get(i);
            String roleKey = roleKeys.get(i);

            AuthorizationServiceUser user = mock(AuthorizationServiceUser.class);
            lenient().when(user.getId()).thenReturn(userId);

            AuthorizationServiceRole role = mock(AuthorizationServiceRole.class);
            when(role.getKey()).thenReturn(roleKey);

            AuthorizationServiceAuthorization auth = mock(AuthorizationServiceAuthorization.class);
            lenient().when(auth.getUser()).thenReturn(user);
            when(auth.getRoles()).thenReturn(List.of(role));

            authorizations.add(auth);
        }

        AuthorizationServiceListAuthorizationsResponse response = mock(AuthorizationServiceListAuthorizationsResponse.class);
        when(response.getAuthorizations()).thenReturn(authorizations);

        return response;
    }
}
