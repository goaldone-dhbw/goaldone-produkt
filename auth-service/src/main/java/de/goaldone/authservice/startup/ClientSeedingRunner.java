package de.goaldone.authservice.startup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds OAuth2 clients from environment variables into the JDBC repository.
 */
@Component
public class ClientSeedingRunner implements ApplicationRunner {

    private final RegisteredClientRepository registeredClientRepository;

    @Value("${FRONTEND_CLIENT_ID:goaldone-web}")
    private String frontendClientId;

    @Value("${FRONTEND_CLIENT_SECRET:}")
    private String frontendClientSecret;

    @Value("${FRONTEND_REDIRECT_URIS:http://localhost:4200/callback}")
    private String frontendRedirectUris;

    @Value("${FRONTEND_POST_LOGOUT_REDIRECT_URIS:http://localhost:4200/}")
    private String frontendPostLogoutRedirectUris;

    @Value("${MGMT_CLIENT_ID:mgmt-client}")
    private String mgmtClientId;

    @Value("${MGMT_CLIENT_SECRET:mgmt-secret}")
    private String mgmtClientSecret;

    public ClientSeedingRunner(RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedFrontendClient();
        seedMgmtClient();
    }

    private void seedFrontendClient() {
        if (registeredClientRepository.findByClientId(frontendClientId) == null) {
            RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(frontendClientId)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("offline_access")
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(false)
                            .requireProofKey(true)
                            .build());

            for (String uri : frontendRedirectUris.split(",")) {
                builder.redirectUri(uri.trim());
            }
            
            for (String uri : frontendPostLogoutRedirectUris.split(",")) {
                builder.postLogoutRedirectUri(uri.trim());
            }

            if (frontendClientSecret != null && !frontendClientSecret.isEmpty()) {
                builder.clientSecret("{noop}" + frontendClientSecret);
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            } else {
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
            }

            registeredClientRepository.save(builder.build());
        }
    }

    private void seedMgmtClient() {
        if (registeredClientRepository.findByClientId(mgmtClientId) == null) {
            RegisteredClient mgmtClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(mgmtClientId)
                    .clientSecret("{noop}" + mgmtClientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("mgmt:admin")
                    .build();
            registeredClientRepository.save(mgmtClient);
        }
    }
}
