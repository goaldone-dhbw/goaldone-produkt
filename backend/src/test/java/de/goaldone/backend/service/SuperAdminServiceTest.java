package de.goaldone.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(superAdminService, "goaldoneOrgId", "org-123");
        ReflectionTestUtils.setField(superAdminService, "goaldoneProjectId", "proj-456");
    }

    @Test
    void listSuperAdmins_ShouldReturnAdmins() throws Exception {
        // Arrange
        when(zitadelClient.listUserIdsByRole(anyString(), anyString(), anyString())).thenReturn(List.of("user-1"));
        
        String userJson = """
            {
                "email": { "email": "admin@test.com" },
                "profile": { "givenName": "John", "familyName": "Doe" },
                "state": "USER_STATE_ACTIVE",
                "details": { "createdDate": "2023-10-27T10:00:00Z" }
            }
            """;
        JsonNode userNode = objectMapper.readTree(userJson);
        when(zitadelClient.getUser("user-1")).thenReturn(Optional.of(userNode));

        // Act
        List<SuperAdminResponse> result = superAdminService.listSuperAdmins();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("admin@test.com");
        assertThat(result.get(0).getFirstName()).isEqualTo("John");
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
        when(userAccountRepository.findByZitadelSub("admin-1")).thenReturn(Optional.of(account));
        when(userAccountRepository.countByUserIdentityId(account.getUserIdentityId())).thenReturn(0L);

        // Act
        superAdminService.deleteSuperAdmin("admin-1", "admin-2");

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
        assertThrows(ResponseStatusException.class, () -> superAdminService.deleteSuperAdmin("last-admin", "last-admin"));
        verify(zitadelClient, never()).deleteUser(anyString());
    }
}
