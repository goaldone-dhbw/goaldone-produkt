package de.goaldone.authservice.startup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ClientSeedingIntegrationTest {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Test
    void shouldSeedDefaultClients() {
        RegisteredClient frontendClient = registeredClientRepository.findByClientId("goaldone-web");
        assertThat(frontendClient).isNotNull();
        assertThat(frontendClient.getRedirectUris()).contains("http://localhost:4200/callback");
        
        RegisteredClient mgmtClient = registeredClientRepository.findByClientId("mgmt-client");
        assertThat(mgmtClient).isNotNull();
        assertThat(mgmtClient.getScopes()).contains("mgmt:admin");
    }
}
