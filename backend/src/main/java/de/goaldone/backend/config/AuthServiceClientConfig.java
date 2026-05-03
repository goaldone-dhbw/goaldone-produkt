package de.goaldone.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the auth-service M2M client using OAuth2 client_credentials grant.
 */
@Configuration
public class AuthServiceClientConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository) {
        OAuth2AuthorizedClientService authorizedClientService =
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build());
        return manager;
    }

    @Bean("authServiceRestClient")
    public RestClient authServiceRestClient(
            OAuth2AuthorizedClientManager clientManager,
            RestClient.Builder restClientBuilder,
            @Value("${auth-service.base-url}") String baseUrl) {
        return restClientBuilder.baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                            .withClientRegistrationId("auth-service-mgmt")
                            .principal("backend-service")
                            .build();
                    OAuth2AuthorizedClient client = clientManager.authorize(authorizeRequest);
                    if (client != null && client.getAccessToken() != null) {
                        request.getHeaders().setBearerAuth(
                                client.getAccessToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
