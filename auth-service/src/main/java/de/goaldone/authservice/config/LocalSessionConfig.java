package de.goaldone.authservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.security.core.session.SessionRegistry;

import javax.sql.DataSource;

/**
 * Configuration for Spring Session with H2 local development backend.
 *
 * This configuration is activated when:
 * - The "dev" profile is active, or
 * - The "local" profile is active
 *
 * It configures JDBC-based session storage using H2 in-memory database,
 * allowing developers to run the application without requiring Redis.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"dev", "local"})
@EnableJdbcHttpSession
public class LocalSessionConfig {

    /**
     * Creates a SessionRegistry for local development.
     * Uses Spring Session JDBC backend with H2 database.
     *
     * @param sessionRepository the session repository configured by Spring
     * @return the session registry for managing user sessions
     */
    @Bean
    public SessionRegistry localSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }
}
