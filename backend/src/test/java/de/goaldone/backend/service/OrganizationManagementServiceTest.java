package de.goaldone.backend.service;

import de.goaldone.backend.client.AuthServiceManagementClient;
import de.goaldone.backend.client.AuthServiceManagementException;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.model.MemberRole;
import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationManagementServiceTest {

    @Mock
    private AuthServiceManagementClient authServiceClient;

    @Mock
    private OrganizationRepository organizationRepository;

    private OrganizationManagementService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationManagementService(authServiceClient, organizationRepository);
    }

    @Test
    void testTC1_HappyPath() {
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(authServiceClient.createInvitation(any(UUID.class), eq("admin@goaldone.de"), isNull(), eq(MemberRole.COMPANY_ADMIN)))
                .thenReturn(UUID.randomUUID());

        OrganizationResponse response = service.createOrganization(req);

        assertNotNull(response);
        assertEquals("GoalDone GmbH", response.getName());
        assertEquals("admin@goaldone.de", response.getAdminEmail());
        verify(organizationRepository).save(any());
        verify(authServiceClient).createInvitation(any(UUID.class), eq("admin@goaldone.de"), isNull(), eq(MemberRole.COMPANY_ADMIN));
    }

    @Test
    void testTC4_OrganizationNameAlreadyExists() {
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createOrganization(req)
        );

        assertEquals("ORGANIZATION_NAME_ALREADY_EXISTS", ex.getMessage());
        verify(organizationRepository, never()).save(any());
        verify(authServiceClient, never()).createInvitation(any(), any(), any(), any());
    }

    @Test
    void testTC5_AuthServiceFails_CompensationExecutes() {
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(authServiceClient.createInvitation(any(UUID.class), eq("admin@goaldone.de"), isNull(), eq(MemberRole.COMPANY_ADMIN)))
                .thenThrow(new AuthServiceManagementException("Upstream error", 502));

        AuthServiceManagementException ex = assertThrows(AuthServiceManagementException.class, () ->
                service.createOrganization(req)
        );

        assertTrue(ex.getMessage().contains("Failed to create organization"));
        verify(organizationRepository).deleteById(any(UUID.class));
        verify(authServiceClient, never()).cancelInvitation(any());
    }
}
