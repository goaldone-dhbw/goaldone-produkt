package de.goaldone.backend.config;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.filter.JitProvisioningFilter;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.security.TenantContextFilter;
import de.goaldone.backend.service.JitProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main security configuration for the application.
 * Configures OAuth2 resource server support, method-level security, CORS, and
 * Just-In-Time (JIT) user provisioning.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    /**
     * Creates a {@link JitProvisioningFilter} bean.
     * This filter ensures that users are provisioned in the local database upon their first request.
     *
     * @param jitProvisioningService the service responsible for provisioning logic
     * @return a new JitProvisioningFilter
     */
    @Bean
    public JitProvisioningFilter jitProvisioningFilter(JitProvisioningService jitProvisioningService) {
        return new JitProvisioningFilter(jitProvisioningService);
    }

    /**
     * Component providing centralized authorization logic for organization-level access.
     */
    @Component("authz")
    public class AuthorizationLogic {
        private final OrganizationRepository organizationRepository;

        public AuthorizationLogic(OrganizationRepository organizationRepository) {
            this.organizationRepository = organizationRepository;
        }

        /**
         * Checks if the currently authenticated user is a member of the specified organization.
         * Super-admins (ROLE_ADMIN) bypass this check.
         *
         * @param orgId the ID of the organization to check
         * @return true if the user has access, false otherwise
         */
        public boolean isMember(UUID orgId) {
            if (orgId == null) {
                return false;
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                return false;
            }

            // Role-based bypass for global admins
            if (authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return true;
            }

            // Resolve the organization's authCompanyId from the database
            String authCompanyId = organizationRepository.findById(orgId)
                    .map(OrganizationEntity::getAuthCompanyId)
                    .orElse(null);

            if (authCompanyId == null) {
                return false;
            }

            // Check organization membership in JWT claims
            List<Map<String, Object>> orgs = jwt.getClaim("orgs");
            if (orgs == null) {
                return false;
            }

            return orgs.stream()
                    .anyMatch(org -> {
                        Object id = org.get("id");
                        return id != null && id.toString().equals(authCompanyId);
                    });
        }
    }

    /**
     * Configures the {@link SecurityFilterChain} for the application.
     * <p>
     * Defines access rules, stateless session management, JWT-based authentication,
     * and registers the JIT provisioning and tenant context filters.
     * </p>
     *
     * @param http the HttpSecurity object to configure
     * @param jitProvisioningFilter the custom filter for user provisioning
     * @param tenantContextFilter the custom filter for tenant context extraction and validation
     * @return the built SecurityFilterChain
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JitProvisioningFilter jitProvisioningFilter, TenantContextFilter tenantContextFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .addFilterAfter(jitProvisioningFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, JitProvisioningFilter.class);
        return http.build();
    }

    /**
     * Configures a {@link JwtAuthenticationConverter} to extract roles from the 'authorities' claim.
     * <p>
     * It maps the list of strings from the {@code authorities} claim to Spring Security roles
     * with a {@code ROLE_} prefix.
     * </p>
     *
     * @return a configured JwtAuthenticationConverter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> authorities = jwt.getClaimAsStringList("authorities");
            if (authorities == null) {
                return List.of();
            }
            return authorities.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return jwtConverter;
    }

    /**
     * Defines the CORS configuration source for the application.
     * Allows requests from specified origins and defines permitted HTTP methods and headers.
     *
     * @return a configured CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
