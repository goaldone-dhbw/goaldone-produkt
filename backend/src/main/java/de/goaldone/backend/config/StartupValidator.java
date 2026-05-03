package de.goaldone.backend.config;

import de.goaldone.backend.client.AuthServiceManagementClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates that the auth-service management API is reachable at startup.
 * Logs a warning on failure — does NOT prevent application startup.
 */
@Component
@Slf4j
public class StartupValidator {

    private final AuthServiceManagementClient authServiceClient;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public StartupValidator(AuthServiceManagementClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateAuthServiceConnection() {
        if ("test".equals(activeProfile) || "local".equals(activeProfile)) {
            log.debug("Skipping auth-service validation in {} profile", activeProfile);
            return;
        }

        log.info("Checking auth-service reachability...");
        if (!authServiceClient.isReachable()) {
            log.warn("AUTH_SERVICE_UNREACHABLE: Auth-service management API is not reachable at startup. Member management operations will fail.");
        } else {
            log.info("Auth-service management API is reachable.");
        }
    }
}
