package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
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

        String grantsJson = """
                {
                  "result": [
                    { "userId": "user-1", "roleKeys": ["USER"] },
                    { "userId": "user-2", "roleKeys": ["COMPANY_ADMIN"] }
                  ]
                }
                """;
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), anyString())).thenReturn(objectMapper.readTree(grantsJson));

        String user1Json = """
                {
                  "id": "user-1",
                  "state": "USER_STATE_ACTIVE",
                  "human": { "email": { "email": "user1@test.com" }, "profile": { "givenName": "User", "familyName": "One" } },
                  "details": { "creationDate": "2023-01-01T00:00:00Z" }
                }
                """;
        String user2Json = """
                {
                  "id": "user-2",
                  "state": "USER_STATE_INITIAL",
                  "human": { "email": { "email": "user2@test.com" }, "profile": { "givenName": "User", "familyName": "Two" } },
                  "details": { "creationDate": "2023-01-02T00:00:00Z" }
                }
                """;
        when(zitadelManagementClient.listUsersByIds(anyList())).thenReturn(List.of(objectMapper.readTree(user1Json), objectMapper.readTree(user2Json)));

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
        assertThat(m1.getAccountId().get()).isNotNull();

        var m2 = response.getMembers().stream().filter(m -> m.getZitadelUserId().equals("user-2")).findFirst().get();
        assertThat(m2.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(m2.getRole()).isEqualTo(MemberRole.COMPANY_ADMIN);
        assertThat(m2.getAccountId().get()).isNull();
    }

    @Test
    void changeMemberRole_Success() throws Exception {
        // Arrange
        UserAccountEntity callerAccount = new UserAccountEntity();
        callerAccount.setOrganizationId(orgId);
        when(userAccountRepository.findByZitadelSub(callerSub)).thenReturn(Optional.of(callerAccount));

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(new OrganizationEntity()));

        String grantJson = """
                { "grantId": "grant-1", "roleKeys": ["USER"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

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

        String grantJson = """
                { "grantId": "grant-1", "roleKeys": ["COMPANY_ADMIN"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

        String allGrantsJson = """
                {
                  "result": [
                    { "userId": "target-user", "roleKeys": ["COMPANY_ADMIN"] },
                    { "userId": "user-3", "roleKeys": ["USER"] }
                  ]
                }
                """;
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(objectMapper.readTree(allGrantsJson));

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

        String grantJson = """
                { "roleKeys": ["USER"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

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

        String grantJson = """
                { "roleKeys": ["USER"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

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

        String grantJson = """
                { "grantId": "grant-1", "roleKeys": ["COMPANY_ADMIN"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

        String allGrantsJson = """
                {
                  "result": [
                    { "userId": "target-user", "roleKeys": ["COMPANY_ADMIN"] },
                    { "userId": "other-admin", "roleKeys": ["COMPANY_ADMIN"] }
                  ]
                }
                """;
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(objectMapper.readTree(allGrantsJson));

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

        String grantJson = """
                { "grantId": "grant-1", "roleKeys": ["COMPANY_ADMIN"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq(callerSub)))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

        String allGrantsJson = """
                {
                  "result": [
                    { "userId": "caller-sub", "roleKeys": ["COMPANY_ADMIN"] },
                    { "userId": "other-admin", "roleKeys": ["COMPANY_ADMIN"] }
                  ]
                }
                """;
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(objectMapper.readTree(allGrantsJson));

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

        String grantJson = """
                { "roleKeys": ["COMPANY_ADMIN"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

        String allGrantsJson = """
                {
                  "result": [
                    { "userId": "target-user", "roleKeys": ["COMPANY_ADMIN"] }
                  ]
                }
                """;
        when(zitadelManagementClient.listAllGrants(anyString(), anyString(), eq("user-org")))
                .thenReturn(objectMapper.readTree(allGrantsJson));

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

        String grantJson = """
                { "grantId": "grant-1", "roleKeys": ["USER"] }
                """;
        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("target-user")))
                .thenReturn(Optional.of(objectMapper.readTree(grantJson)));

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
}
