package de.goaldone.backend.service;

import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.config.ZitadelManagementProperties;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.exception.EmailAlreadyInUseException;
import de.goaldone.backend.exception.LastSuperAdminException;
import de.goaldone.backend.exception.UserNotFoundException;
import de.goaldone.backend.exception.ZitadelUpstreamException;
import de.goaldone.backend.model.CreateSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminListResponse;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private ZitadelManagementClient zitadelClient;

    @Mock
    private ZitadelManagementProperties properties;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private LinkTokenRepository linkTokenRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    private static final String GOALDONE_PROJECT_ID = "project-123";
    private static final String GOALDONE_ORG_ID = "org-456";

    // ========== TEST CASE 1: Super-admin successfully added ==========
    @Test
    void createSuperAdmin_success() {
        String email = "admin2@goaldone.de";
        String userId = UUID.randomUUID().toString();
        CreateSuperAdminRequest request = new CreateSuperAdminRequest(email);

        // Mock the flow
        when(properties.getGoaldoneProjectId()).thenReturn(GOALDONE_PROJECT_ID);
        when(properties.getGoaldoneOrgId()).thenReturn(GOALDONE_ORG_ID);
        when(zitadelClient.userExistsByEmail(email)).thenReturn(false);
        when(zitadelClient.addHumanUser(email)).thenReturn(userId);
        doNothing().when(zitadelClient).addUserGrant(userId, GOALDONE_PROJECT_ID, "SUPER_ADMIN");
        doNothing().when(zitadelClient).createInviteCode(userId);

        ZitadelManagementClient.UserDetail userDetail = new ZitadelManagementClient.UserDetail(
                userId, email, email, "Admin", "Two", "ACTIVE", "2026-04-20T10:00:00+00:00"
        );
        when(zitadelClient.getUserById(userId)).thenReturn(userDetail);

        SuperAdminResponse response = superAdminService.createSuperAdmin(request);

        assertNotNull(response);
        assertEquals(UUID.fromString(userId), response.getUserId());
        assertEquals(email, response.getEmail());
        verify(zitadelClient).addHumanUser(email);
        verify(zitadelClient).addUserGrant(userId, GOALDONE_PROJECT_ID, "SUPER_ADMIN");
        verify(zitadelClient).createInviteCode(userId);
        verify(zitadelClient, never()).deleteUser(any());
    }

    // ========== TEST CASE 2: Super-admin deleted by another super-admin ==========
    @Test
    void deleteSuperAdmin_success() {
        UUID adminToDelete = UUID.randomUUID();
        String adminToDeleteStr = adminToDelete.toString();
        UUID adminIdentityId = UUID.randomUUID();
        UUID adminAccountId = UUID.randomUUID();

        when(properties.getGoaldoneProjectId()).thenReturn(GOALDONE_PROJECT_ID);
        when(properties.getGoaldoneOrgId()).thenReturn(GOALDONE_ORG_ID);

        // Mock grants list (2 super-admins)
        ZitadelManagementClient.GrantsListResponse grantsResponse = new ZitadelManagementClient.GrantsListResponse(
                List.of(
                        new ZitadelManagementClient.GrantsListResponse.Grant(adminToDeleteStr),
                        new ZitadelManagementClient.GrantsListResponse.Grant("admin-other")
                ),
                2
        );
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);
        doNothing().when(zitadelClient).deleteUser(adminToDeleteStr);

        UserAccountEntity userAccount = new UserAccountEntity();
        userAccount.setId(adminAccountId);
        userAccount.setZitadelSub(adminToDeleteStr);
        userAccount.setUserIdentityId(adminIdentityId);
        when(userAccountRepository.findByZitadelSub(adminToDeleteStr)).thenReturn(Optional.of(userAccount));
        when(userAccountRepository.countByUserIdentityId(adminIdentityId)).thenReturn(0L);
        when(userIdentityRepository.existsById(adminIdentityId)).thenReturn(true);
        doNothing().when(linkTokenRepository).deleteByInitiatorAccountId(adminAccountId);
        doNothing().when(userAccountRepository).delete(any());
        doNothing().when(userIdentityRepository).deleteById(adminIdentityId);

        superAdminService.deleteSuperAdmin(adminToDelete);

        verify(zitadelClient).deleteUser(adminToDeleteStr);
        verify(userAccountRepository).delete(userAccount);
        verify(userIdentityRepository).deleteById(adminIdentityId);
    }

    // ========== TEST CASE 3: StartupValidator checks config ==========
    // (tested separately in StartupValidatorTest)

    // ========== TEST CASE 4: List super-admins ==========
    @Test
    void listSuperAdmins_success() {
        String admin1Id = UUID.randomUUID().toString();
        String admin2Id = UUID.randomUUID().toString();

        ZitadelManagementClient.GrantsListResponse grantsResponse = new ZitadelManagementClient.GrantsListResponse(
                List.of(
                        new ZitadelManagementClient.GrantsListResponse.Grant(admin1Id),
                        new ZitadelManagementClient.GrantsListResponse.Grant(admin2Id)
                ),
                2
        );
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        ZitadelManagementClient.UserDetail user1 = new ZitadelManagementClient.UserDetail(
                admin1Id, "admin1@goaldone.de", "admin1@goaldone.de", "Admin", "One", "ACTIVE", "2026-04-20T10:00:00+00:00"
        );
        ZitadelManagementClient.UserDetail user2 = new ZitadelManagementClient.UserDetail(
                admin2Id, "admin2@goaldone.de", "admin2@goaldone.de", "Admin", "Two", "ACTIVE", "2026-04-20T11:00:00+00:00"
        );
        when(zitadelClient.getUserById(admin1Id)).thenReturn(user1);
        when(zitadelClient.getUserById(admin2Id)).thenReturn(user2);

        SuperAdminListResponse response = superAdminService.listSuperAdmins();

        assertNotNull(response);
        assertEquals(2, response.getSuperAdmins().size());
        assertEquals(UUID.fromString(admin1Id), response.getSuperAdmins().get(0).getUserId());
        assertEquals(UUID.fromString(admin2Id), response.getSuperAdmins().get(1).getUserId());
    }

    // ========== TEST CASE 5: Cannot delete last super-admin (self) ==========
    @Test
    void deleteSuperAdmin_lastAdminSelfDelete_throws() {
        UUID adminId = UUID.randomUUID();
        String adminIdStr = adminId.toString();

        ZitadelManagementClient.GrantsListResponse grantsResponse = new ZitadelManagementClient.GrantsListResponse(
                List.of(new ZitadelManagementClient.GrantsListResponse.Grant(adminIdStr)),
                1
        );
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        assertThrows(LastSuperAdminException.class, () -> superAdminService.deleteSuperAdmin(adminId));
        verify(zitadelClient, never()).deleteUser(any());
    }

    // ========== TEST CASE 6: Cannot delete last super-admin (other delete) ==========
    @Test
    void deleteSuperAdmin_lastAdminOtherDelete_throws() {
        UUID adminToDelete = UUID.randomUUID();
        String adminToDeleteStr = adminToDelete.toString();

        ZitadelManagementClient.GrantsListResponse grantsResponse = new ZitadelManagementClient.GrantsListResponse(
                List.of(new ZitadelManagementClient.GrantsListResponse.Grant(adminToDeleteStr)),
                1
        );
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        assertThrows(LastSuperAdminException.class, () -> superAdminService.deleteSuperAdmin(adminToDelete));
        verify(zitadelClient, never()).deleteUser(any());
    }

    // ========== TEST CASE 7: Zitadel error during add (compensation) ==========
    @Test
    void createSuperAdmin_addGrantFails_compensates() {
        String email = "admin2@goaldone.de";
        String userId = "user-xyz";
        CreateSuperAdminRequest request = new CreateSuperAdminRequest(email);

        when(properties.getGoaldoneProjectId()).thenReturn(GOALDONE_PROJECT_ID);
        when(properties.getGoaldoneOrgId()).thenReturn(GOALDONE_ORG_ID);
        when(zitadelClient.userExistsByEmail(email)).thenReturn(false);
        when(zitadelClient.addHumanUser(email)).thenReturn(userId);
        doThrow(new ZitadelUpstreamException("Zitadel error"))
                .when(zitadelClient).addUserGrant(userId, GOALDONE_PROJECT_ID, "SUPER_ADMIN");
        doNothing().when(zitadelClient).deleteUser(userId);

        assertThrows(ZitadelUpstreamException.class, () -> superAdminService.createSuperAdmin(request));
        verify(zitadelClient).deleteUser(userId);
    }

    // ========== TEST CASE 8: StartupValidator finds no Goaldone org ==========
    // (tested in StartupValidatorTest)

    // ========== TEST CASE 9: StartupValidator finds no super-admin ==========
    // (tested in StartupValidatorTest)

    // ========== TEST CASE 10: Access without SUPER_ADMIN role ==========
    // (tested in SuperAdminControllerIntegrationTest with @PreAuthorize)

    // ========== TEST CASE 11: Access without token ==========
    // (tested in SuperAdminControllerIntegrationTest with no Bearer)

    // ========== TEST CASE 12: Delete non-existent user ==========
    @Test
    void deleteSuperAdmin_userNotFound_throws() {
        UUID adminToDelete = UUID.randomUUID();
        String adminToDeleteStr = adminToDelete.toString();

        ZitadelManagementClient.GrantsListResponse grantsResponse = new ZitadelManagementClient.GrantsListResponse(
                List.of(
                        new ZitadelManagementClient.GrantsListResponse.Grant("admin-other"),
                        new ZitadelManagementClient.GrantsListResponse.Grant("admin-other-2")
                ),
                2
        );
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);
        doThrow(new UserNotFoundException("User not found"))
                .when(zitadelClient).deleteUser(adminToDeleteStr);

        assertThrows(UserNotFoundException.class, () -> superAdminService.deleteSuperAdmin(adminToDelete));
    }

    // ========== TEST CASE 13: Invalid email format ==========
    // (tested in SuperAdminControllerIntegrationTest with Bean Validation)

    // ========== TEST CASE 14: Email already exists ==========
    @Test
    void createSuperAdmin_emailAlreadyExists_throws() {
        String email = "existing@example.com";
        CreateSuperAdminRequest request = new CreateSuperAdminRequest(email);

        when(zitadelClient.userExistsByEmail(email)).thenReturn(true);

        assertThrows(EmailAlreadyInUseException.class, () -> superAdminService.createSuperAdmin(request));
        verify(zitadelClient, never()).addHumanUser(any());
    }
}
