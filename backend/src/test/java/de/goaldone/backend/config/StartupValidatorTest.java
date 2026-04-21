package de.goaldone.backend.config;

import de.goaldone.backend.client.ZitadelManagementClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartupValidatorTest {

    @Mock
    private ZitadelManagementClient zitadelClient;

    @Mock
    private ZitadelManagementProperties properties;

    @Mock
    private ContextRefreshedEvent event;

    @InjectMocks
    private StartupValidator validator;

    private static final String GOALDONE_ORG_ID = "org-123";

    @BeforeEach
    void setUp() {
        when(properties.getGoaldoneOrgId()).thenReturn(GOALDONE_ORG_ID);
    }

    // TC1: Organization exists, super-admin exists (happy path)
    @Test
    void onApplicationEvent_orgAndSuperAdminFound_succeeds() {
        ZitadelManagementClient.OrganizationsListResponse orgResponse =
            new ZitadelManagementClient.OrganizationsListResponse(Collections.emptyList(), 1);
        ZitadelManagementClient.GrantsListResponse grantsResponse =
            new ZitadelManagementClient.GrantsListResponse(Collections.emptyList(), 1);

        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID)).thenReturn(orgResponse);
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        assertDoesNotThrow(() -> validator.onApplicationEvent(event));

        verify(zitadelClient).listOrganizationsById(GOALDONE_ORG_ID);
        verify(zitadelClient).listSuperAdminGrants();
    }

    // TC2: Organization missing (totalResult = 0)
    @Test
    void onApplicationEvent_orgMissing_noException() {
        ZitadelManagementClient.OrganizationsListResponse orgResponse =
            new ZitadelManagementClient.OrganizationsListResponse(Collections.emptyList(), 0);
        ZitadelManagementClient.GrantsListResponse grantsResponse =
            new ZitadelManagementClient.GrantsListResponse(Collections.emptyList(), 1);

        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID)).thenReturn(orgResponse);
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        // Should not throw, but should log error (we don't assert on logs in unit test)
        assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        verify(zitadelClient).listOrganizationsById(GOALDONE_ORG_ID);
    }

    // TC3: Organization check throws exception
    @Test
    void onApplicationEvent_orgCheckThrows_swallowsException() {
        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID))
            .thenThrow(new RuntimeException("Zitadel connection failed"));

        ZitadelManagementClient.GrantsListResponse grantsResponse =
            new ZitadelManagementClient.GrantsListResponse(Collections.emptyList(), 1);
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        // Should not re-throw the exception
        assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        verify(zitadelClient).listOrganizationsById(GOALDONE_ORG_ID);
        // Should still attempt super-admin check after org check failed
        verify(zitadelClient).listSuperAdminGrants();
    }

    // TC4: No super-admin found (totalResult = 0)
    @Test
    void onApplicationEvent_noSuperAdminFound_noException() {
        ZitadelManagementClient.OrganizationsListResponse orgResponse =
            new ZitadelManagementClient.OrganizationsListResponse(Collections.emptyList(), 1);
        ZitadelManagementClient.GrantsListResponse grantsResponse =
            new ZitadelManagementClient.GrantsListResponse(Collections.emptyList(), 0);

        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID)).thenReturn(orgResponse);
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        // Should not throw, but should log warning
        assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        verify(zitadelClient).listSuperAdminGrants();
    }

    // TC5: Super-admin check throws exception
    @Test
    void onApplicationEvent_superAdminCheckThrows_swallowsException() {
        ZitadelManagementClient.OrganizationsListResponse orgResponse =
            new ZitadelManagementClient.OrganizationsListResponse(Collections.emptyList(), 1);

        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID)).thenReturn(orgResponse);
        when(zitadelClient.listSuperAdminGrants())
            .thenThrow(new RuntimeException("Zitadel API error"));

        // Should not re-throw the exception
        assertDoesNotThrow(() -> validator.onApplicationEvent(event));
        verify(zitadelClient).listSuperAdminGrants();
    }

    // TC6: onApplicationEvent called twice (should execute only once)
    @Test
    void onApplicationEvent_calledTwice_executesOnlyOnce() {
        ZitadelManagementClient.OrganizationsListResponse orgResponse =
            new ZitadelManagementClient.OrganizationsListResponse(Collections.emptyList(), 1);
        ZitadelManagementClient.GrantsListResponse grantsResponse =
            new ZitadelManagementClient.GrantsListResponse(Collections.emptyList(), 1);

        when(zitadelClient.listOrganizationsById(GOALDONE_ORG_ID)).thenReturn(orgResponse);
        when(zitadelClient.listSuperAdminGrants()).thenReturn(grantsResponse);

        // Call twice
        validator.onApplicationEvent(event);
        validator.onApplicationEvent(event);

        // Verify mocks were called exactly once (not twice)
        verify(zitadelClient, times(1)).listOrganizationsById(GOALDONE_ORG_ID);
        verify(zitadelClient, times(1)).listSuperAdminGrants();
    }
}
