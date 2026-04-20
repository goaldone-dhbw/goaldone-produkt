package de.goaldone.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zitadel.management")
public class ZitadelManagementProperties {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private String goaldoneOrgId;
    private String goaldoneProjectId;
}
