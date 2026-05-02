package de.goaldone.authservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SessionSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sessionShouldBePersistedInDatabase() throws Exception {
        // Trigger session creation
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());

        // Check the database for the session
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList("SELECT * FROM SPRING_SESSION");
        assertThat(sessions).isNotEmpty();
    }
}
