package de.goaldone.backend.service;

import de.goaldone.backend.model.OrganizationResponse;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.ZitadelApiException;
import de.goaldone.backend.model.CreateOrganizationRequest;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationManagementServiceTest {

    @Mock
    private ZitadelManagementClient zitadelManagementClient;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserAccountDeletionService userAccountDeletionService;

    private OrganizationManagementService service;

    private void initService() {
        service = new OrganizationManagementService(zitadelManagementClient, organizationRepository, userAccountRepository, userAccountDeletionService);
        // Use reflection to set the projectId and mainOrgId fields since they are private with @Value
        try {
            Field projectField = service.getClass().getDeclaredField("projectId");
            projectField.setAccessible(true);
            projectField.set(service, "test-project-id");

            Field orgField = service.getClass().getDeclaredField("mainOrgId");
            orgField.setAccessible(true);
            orgField.set(service, "test-main-org-id");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testTC1_HappyPath() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("admin@goaldone.de")).thenReturn(false);
        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(zitadelManagementClient.addOrganization("GoalDone GmbH")).thenReturn("org-123");
        when(zitadelManagementClient.addHumanUser("org-123", "admin@goaldone.de", "Max", "Mustermann")).thenReturn("user-xyz");

        OrganizationResponse response = service.createOrganization(req);

        assertNotNull(response);
        assertEquals("org-123", response.getZitadelOrganizationId());
        assertEquals("GoalDone GmbH", response.getName());
        assertEquals("admin@goaldone.de", response.getAdminEmail());

        InOrder inOrder = inOrder(zitadelManagementClient);
        inOrder.verify(zitadelManagementClient).emailExists("admin@goaldone.de");
        inOrder.verify(zitadelManagementClient).addOrganization("GoalDone GmbH");
        inOrder.verify(zitadelManagementClient).addHumanUser("org-123", "admin@goaldone.de", "Max", "Mustermann");
        inOrder.verify(zitadelManagementClient).addUserGrant("user-xyz", "test-main-org-id", "test-project-id", "COMPANY_ADMIN");
        inOrder.verify(zitadelManagementClient).createInviteCode("user-xyz");
    }

    @Test
    void testTC3_EmailAlreadyExists() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("existing@example.com")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("existing@example.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createOrganization(req)
        );

        assertEquals("EMAIL_ALREADY_IN_USE", ex.getMessage());
        verify(zitadelManagementClient, never()).addOrganization(anyString());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void testTC4_OrganizationNameAlreadyExists() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("admin@goaldone.de")).thenReturn(false);
        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                service.createOrganization(req)
        );

        assertEquals("ORGANIZATION_NAME_ALREADY_EXISTS", ex.getMessage());
        verify(zitadelManagementClient, never()).addOrganization(anyString());
    }

    @Test
    void testTC5_AddHumanUserFails_CompensationExecutes() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("admin@goaldone.de")).thenReturn(false);
        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(zitadelManagementClient.addOrganization("GoalDone GmbH")).thenReturn("org-123");
        when(organizationRepository.save(any())).thenReturn(null);
        when(zitadelManagementClient.addHumanUser("org-123", "admin@goaldone.de", "Max", "Mustermann"))
                .thenThrow(new ZitadelApiException("500"));

        ZitadelApiException ex = assertThrows(ZitadelApiException.class, () ->
                service.createOrganization(req)
        );

        assertTrue(ex.getMessage().contains("Failed to create organization"));

        // Verify compensation: deleteOrganization and deleteById were called
        verify(zitadelManagementClient).deleteOrganization("org-123");
        verify(organizationRepository).deleteById(any(UUID.class));
    }

    @Test
    void testTC6_CreateInviteCodeFails_FullCompensation() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("admin@goaldone.de")).thenReturn(false);
        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(zitadelManagementClient.addOrganization("GoalDone GmbH")).thenReturn("org-123");
        when(organizationRepository.save(any())).thenReturn(null);
        when(zitadelManagementClient.addHumanUser("org-123", "admin@goaldone.de", "Max", "Mustermann")).thenReturn("user-xyz");
        doThrow(new ZitadelApiException("500")).when(zitadelManagementClient).createInviteCode("user-xyz");

        ZitadelApiException ex = assertThrows(ZitadelApiException.class, () ->
                service.createOrganization(req)
        );

        // Verify full compensation chain
        verify(zitadelManagementClient).deleteUser("user-xyz");
        verify(organizationRepository).deleteById(any(UUID.class));
        verify(zitadelManagementClient).deleteOrganization("org-123");
    }

    @Test
    void testTC7_CompensationPartiallyFails_LogsButContinues() {
        initService();
        CreateOrganizationRequest req = new CreateOrganizationRequest()
                .name("GoalDone GmbH")
                .adminEmail("admin@goaldone.de")
                .adminFirstName("Max")
                .adminLastName("Mustermann");

        when(zitadelManagementClient.emailExists("admin@goaldone.de")).thenReturn(false);
        when(organizationRepository.existsByName("GoalDone GmbH")).thenReturn(false);
        when(zitadelManagementClient.addOrganization("GoalDone GmbH")).thenReturn("org-123");
        when(organizationRepository.save(any())).thenReturn(null);
        when(zitadelManagementClient.addHumanUser("org-123", "admin@goaldone.de", "Max", "Mustermann")).thenReturn("user-xyz");
        doThrow(new ZitadelApiException("500")).when(zitadelManagementClient).createInviteCode("user-xyz");
        doThrow(new RuntimeException("Delete failed")).when(zitadelManagementClient).deleteUser("user-xyz");

        ZitadelApiException ex = assertThrows(ZitadelApiException.class, () ->
                service.createOrganization(req)
        );

        // Verify that despite deleteUser failure, the rest of compensation continues
        verify(zitadelManagementClient).deleteUser("user-xyz");
        verify(organizationRepository).deleteById(any(UUID.class));
        verify(zitadelManagementClient).deleteOrganization("org-123");
    }
}
