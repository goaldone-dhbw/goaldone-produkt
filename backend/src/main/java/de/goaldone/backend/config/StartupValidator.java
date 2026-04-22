package de.goaldone.backend.config;

import de.goaldone.backend.client.ZitadelManagementClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupValidator {

    private final ZitadelManagementClient zitadelClient;

    @Value("${zitadel.goaldone.org-id}")
    private String goaldoneOrgId;

    @Value("${zitadel.goaldone.project-id}")
    private String goaldoneProjectId;

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    @EventListener(ApplicationReadyEvent.class)
    public void validateZitadelConfiguration() {
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
