package de.goaldone.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for interacting with the Zitadel OIDC UserInfo endpoint.
 * Provides access to basic user profile information for an authenticated user.
 */
@Component
public class ZitadelUserInfoClient {

    private final RestClient restClient;

    /**
     * Constructs a new ZitadelUserInfoClient.
     *
     * @param issuerUri the OIDC issuer URI used to construct the UserInfo endpoint URL
     */
    public ZitadelUserInfoClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        this.restClient = RestClient.builder().baseUrl(issuerUri + "/oidc/v1/userinfo").build();
    }

    /**
     * Retrieves user information from the Zitadel UserInfo endpoint.
     *
     * @param accessToken the OIDC access token of the user
     * @return a ZitadelUserInfo object containing the user's profile data
     */
    public ZitadelUserInfo getUserInfo(String accessToken) {
        return restClient.get().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(ZitadelUserInfo.class);
    }
}
