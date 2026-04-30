package de.goaldone.backend.config;

import com.zitadel.Zitadel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Zitadel Java SDK.
 * Provides a {@link Zitadel} bean for accessing the official Zitadel Management API.
 */
@Configuration
public class ZitadelConfig {

    /**
     * Creates a Zitadel SDK client using a static access token (service account token).
     *
     * @param issuerUri            the Zitadel issuer URI from OAuth2 configuration
     * @param serviceAccountToken  the service account token for authentication
     * @return a configured {@link Zitadel} client instance
     */
    @Bean
    public Zitadel zitadel(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${zitadel.service-account-token}") String serviceAccountToken) {
        return Zitadel.withAccessToken(issuerUri, serviceAccountToken);
    }
}
