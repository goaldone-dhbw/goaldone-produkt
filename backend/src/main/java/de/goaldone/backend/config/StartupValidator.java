package de.goaldone.backend.config;

import de.goaldone.backend.client.ZitadelManagementClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validator component that performs sanity checks on the Zitadel configuration during application startup.
 * It ensures that the required organizational structure and administrative roles are present.
 * Validation is skipped in test profiles to allow WireMock stubs to be set up.
 */
@Component
@Slf4j
public class StartupValidator {

    private final ZitadelManagementClient zitadelClient;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Constructs a new StartupValidator.
     *
     * @param zitadelClient the client used to communicate with Zitadel
     */
    public StartupValidator(ZitadelManagementClient zitadelClient) {
        this.zitadelClient = zitadelClient;
    }

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Validates the Zitadel configuration once the application is ready.
     * <p>
     * Checks if:
     * 1. The configured Goaldone organization exists in Zitadel.
     * 2. At least one user with the {@code SUPER_ADMIN} role is present.
     * </p>
     * Validation is skipped in test profiles since WireMock stubs are set up after context initialization.
     * Any discrepancies are logged as errors or warnings.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateZitadelConfiguration() {
        // Skip validation in test profiles since WireMock stubs are not yet configured
        if ("test".equals(activeProfile) || "local".equals(activeProfile)) {
            log.debug("Skipping Zitadel configuration validation in {} profile", activeProfile);
            return;
        }

        log.info("Starting Zitadel configuration validation...");

        // 1. Check if Goaldone Org exists
        if (!zitadelClient.organizationExists(goaldoneOrgId)) {
            log.error("GOALDONE_ORG_MISSING: Organization with ID {} not found in Zitadel", goaldoneOrgId);
        } else {
            log.info("Zitadel Org {} validated.", goaldoneOrgId);
        }

        // 2. Check if at least one SUPER_ADMIN is present
        try {
            List<String> superAdmins = zitadelClient.listUserIdsByRole(goaldoneOrgId, goaldoneProjectId, ROLE_SUPER_ADMIN);
            if (superAdmins.isEmpty()) {
                log.warn("NO_SUPER_ADMIN_PRESENT: No users with role {} found in organization {}", ROLE_SUPER_ADMIN, goaldoneOrgId);
            } else {
                log.info("Found {} Super-Admins in Zitadel.", superAdmins.size());
            }
        } catch (Exception e) {
            log.error("Failed to validate Super-Admin presence: {}", e.getMessage());
        }

        log.info("Zitadel configuration validation completed.");
    }
}
