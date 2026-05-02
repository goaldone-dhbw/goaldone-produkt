package de.goaldone.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:app_context_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@ActiveProfiles("local")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
