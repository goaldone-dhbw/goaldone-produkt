package de.goaldone.backend.service;

import com.zitadel.model.*;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.model.SuperAdminResponse;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private ZitadelManagementClient zitadelClient;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(superAdminService, "goaldoneOrgId", "org-123");
        ReflectionTestUtils.setField(superAdminService, "goaldoneProjectId", "proj-456");
    }

    @Test
    void listSuperAdmins_ShouldReturnAdmins() throws Exception {
        // Arrange
        when(zitadelClient.listUserIdsByRole(anyString(), anyString(), anyString())).thenReturn(List.of("user-1"));

        UserServiceUser user = buildUser("user-1", "USER_STATE_ACTIVE", "admin@test.com", "John", "Doe", "2023-10-27T10:00:00Z");
        when(zitadelClient.getUser("user-1")).thenReturn(Optional.of(user));

        // Act
        List<SuperAdminResponse> result = superAdminService.listSuperAdmins();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEmail()).isEqualTo("admin@test.com");
        assertThat(result.getFirst().getFirstName()).isEqualTo("John");
    }

    @Test
    void inviteSuperAdmin_Success() {
        // Arrange
        InviteSuperAdminRequest request = new InviteSuperAdminRequest();
        request.setEmail("new@admin.com");

        when(zitadelClient.emailExists("new@admin.com")).thenReturn(false);
        when(zitadelClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("user-new");

        // Act
        superAdminService.inviteSuperAdmin(request);

        // Assert
        verify(zitadelClient).addHumanUser("org-123", "new@admin.com", "Super", "Admin");
        verify(zitadelClient).addUserGrant("user-new", "org-123", "proj-456", "SUPER_ADMIN");
        verify(zitadelClient).createInviteCode("user-new");
    }

    @Test
    void inviteSuperAdmin_CompensationFlow() {
        // Arrange
        InviteSuperAdminRequest request = new InviteSuperAdminRequest();
        request.setEmail("error@admin.com");

        when(zitadelClient.emailExists("error@admin.com")).thenReturn(false);
        when(zitadelClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("user-err");
        doThrow(new RuntimeException("API Error")).when(zitadelClient).addUserGrant(anyString(), anyString(), anyString(), anyString());

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> superAdminService.inviteSuperAdmin(request));
        verify(zitadelClient).deleteUser("user-err");
    }

    @Test
    void deleteSuperAdmin_Success() {
        // Arrange
        when(zitadelClient.listUserIdsByRole(anyString(), anyString(), anyString())).thenReturn(List.of("admin-1", "admin-2"));
        UserAccountEntity account = new UserAccountEntity();
        account.setId(UUID.randomUUID());
        account.setUserIdentityId(UUID.randomUUID());
        when(userAccountRepository.findByAuthUserId("admin-1")).thenReturn(Optional.of(account));
        when(userAccountRepository.countByUserIdentityId(account.getUserIdentityId())).thenReturn(0L);

        // Act
        superAdminService.deleteSuperAdmin("admin-1");

        // Assert
        verify(zitadelClient).deleteUser("admin-1");
        verify(userAccountRepository).delete(account);
        verify(userIdentityRepository).deleteById(account.getUserIdentityId());
    }

    @Test
    void deleteSuperAdmin_PreventsLastAdminDeletion() {
        // Arrange
        when(zitadelClient.listUserIdsByRole(anyString(), anyString(), anyString())).thenReturn(List.of("last-admin"));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> superAdminService.deleteSuperAdmin("last-admin"));
        verify(zitadelClient, never()).deleteUser(anyString());
    }

    // Helper method for building SDK objects using mocks
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
        lenient().when(user.getUserId()).thenReturn(userId);  // Not used by SuperAdminService but kept for consistency with buildUser
        when(user.getState()).thenReturn(userState);
        when(user.getHuman()).thenReturn(human);
        when(user.getDetails()).thenReturn(details);

        return user;
    }
}
