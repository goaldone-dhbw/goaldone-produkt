package de.goaldone.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mgmt_security_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class ManagementSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenAccessingManagementApiWithoutToken_thenUnauthorized() throws Exception {
        this.mockMvc.perform(get("/api/v1/test"))
                .andExpect(status().isUnauthorized());
    }

    // We can use @WithMockUser to simulate a user, but for M2M we want to ensure it works with JWT
    // However, testing the actual M2M flow (getting token from auth server and using it) 
    // is better done by ensuring the filter chain is correctly configured.

    @RestController
    static class TestController {
        @GetMapping("/api/v1/test")
        public String test() {
            return "ok";
        }
    }
}
