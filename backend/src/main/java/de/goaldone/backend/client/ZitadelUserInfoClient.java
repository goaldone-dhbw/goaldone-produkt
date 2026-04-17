package de.goaldone.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ZitadelUserInfoClient {

    private final RestClient restClient;

    public ZitadelUserInfoClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        this.restClient = RestClient.builder().baseUrl(issuerUri + "/oidc/v1/userinfo").build();
    }

    public ZitadelUserInfo getUserInfo(String accessToken) {
        return restClient.get().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(ZitadelUserInfo.class);
    }
}
