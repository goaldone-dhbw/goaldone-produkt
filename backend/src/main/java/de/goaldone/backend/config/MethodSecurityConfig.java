package de.goaldone.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Configures method-level security for custom authorization expressions.
 *
 * Enables @PreAuthorize annotations and custom security expressions like
 * hasOrgRole(...) via CustomMethodSecurityExpressionRoot.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

}
