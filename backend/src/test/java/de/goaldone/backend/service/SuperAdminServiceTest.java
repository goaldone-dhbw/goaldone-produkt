package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.model.InviteSuperAdminRequest;
import de.goaldone.backend.repository.MembershipRepository;
import de.goaldone.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SuperAdminService.
 * Tests super admin operations (invite, list, delete).
 */
@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private AuthServiceManagementClient authServiceClient;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    @Test
    void listSuperAdmins_CallsService() {
        // Act
        superAdminService.listSuperAdmins();

        // Assert - method completes without exception
    }

    @Test
    void inviteSuperAdmin_Success() {
        // Arrange
        InviteSuperAdminRequest request = new InviteSuperAdminRequest();
        request.setEmail("newadmin@goaldone.de");

        UUID invitationId = UUID.randomUUID();
        when(authServiceClient.createInvitation(any(), any(), any(), any())).thenReturn(invitationId);

        // Act
        superAdminService.inviteSuperAdmin(request);

        // Assert
        verify(authServiceClient).createInvitation(any(), any(), any(), any());
    }
}
