package de.goaldone.backend.config;

import de.goaldone.backend.client.ZitadelManagementClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupValidator implements ApplicationListener<ContextRefreshedEvent> {
    private final ZitadelManagementClient zitadelClient;
    private final ZitadelManagementProperties properties;
    private volatile boolean initialized = false;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Run only once on first context refresh
        if (initialized) {
            return;
        }
        initialized = true;

        validateGoaldoneOrganization();
        validateSuperAdminPresence();
    }

    private void validateGoaldoneOrganization() {
        try {
            var response = zitadelClient.listOrganizationsById(properties.getGoaldoneOrgId());
            if (response.totalResult() != 1) {
                log.error("GOALDONE_ORG_MISSING: Expected exactly 1 organization with ID {}, found {}",
                        properties.getGoaldoneOrgId(), response.totalResult());
            }
        } catch (Exception e) {
            log.error("GOALDONE_ORG_MISSING: Failed to verify Goaldone organization: {}", e.getMessage());
        }
    }

    private void validateSuperAdminPresence() {
        try {
            var response = zitadelClient.listSuperAdminGrants();
            if (response.totalResult() < 1) {
                log.warn("NO_SUPER_ADMIN_PRESENT: No super-admins found in Goaldone organization");
            }
        } catch (Exception e) {
            log.error("NO_SUPER_ADMIN_PRESENT: Failed to verify super-admin presence: {}", e.getMessage());
        }
    }
}
