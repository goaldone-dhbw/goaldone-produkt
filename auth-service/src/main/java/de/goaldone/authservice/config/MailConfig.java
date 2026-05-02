package de.goaldone.authservice.config;

import de.goaldone.authservice.service.LocalMailService;
import de.goaldone.authservice.service.MailService;
import de.goaldone.authservice.service.SmtpMailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Configuration for the {@link MailService}.
 */
@Configuration
public class MailConfig {

    @Bean
    @Profile("!prod")
    public MailService localMailService() {
        return new LocalMailService();
    }

    @Bean
    @Profile("prod")
    public MailService smtpMailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        return new SmtpMailService(mailSender, templateEngine);
    }
}
