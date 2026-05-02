package de.goaldone.authservice.service;

import de.goaldone.authservice.config.MailConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class MailServiceTests {

    @SpringBootTest(classes = {MailConfig.class})
    @ActiveProfiles("local")
    @Nested
    class LocalProfileTest {
        @Autowired
        private MailService mailService;

        @Test
        void shouldLoadLocalMailService() {
            assertThat(mailService).isInstanceOf(LocalMailService.class);
        }

        @Test
        void localMailServiceShouldNotThrowException() {
            mailService.sendInvitation("test@example.com", "http://link", "Company");
            mailService.sendPasswordReset("test@example.com", "http://reset");
        }
    }

    @SpringBootTest(classes = {MailConfig.class})
    @ActiveProfiles("prod")
    @Nested
    class ProdProfileTest {
        @Autowired
        private MailService mailService;

        @MockitoBean
        private JavaMailSender mailSender;

        @MockitoBean
        private SpringTemplateEngine templateEngine;

        @Test
        void shouldLoadSmtpMailService() {
            assertThat(mailService).isInstanceOf(SmtpMailService.class);
        }
    }
}
