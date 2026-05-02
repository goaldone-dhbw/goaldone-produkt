package de.goaldone.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class KeyPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPersistKeysAndReturnCacheHeaders() throws Exception {
        // Verify key file exists
        File keyFile = new File("var/auth-service/keys/jwk.json");
        assertThat(keyFile).exists();
        String content = Files.readString(keyFile.toPath());
        assertThat(content).contains("kty").contains("RSA");

        // Verify JWKS endpoint headers
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=3600"));
    }
}
