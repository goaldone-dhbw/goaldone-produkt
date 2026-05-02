package de.goaldone.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.security.core.session.SessionRegistry;

/**
 * Configuration for Spring Session with JDBC backend.
 *
 * This configuration is only active for dev and local profiles.
 * For production environments, Redis-based session storage should be configured separately.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "local"})
@EnableJdbcHttpSession
public class SessionConfig {

    /**
     * Exposes the {@link SessionRegistry} bean using Spring Session's implementation.
     * This allows finding and invalidating user sessions.
     *
     * @param sessionRepository the session repository.
     * @return the session registry.
     */
    @Bean
    public SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }
}
