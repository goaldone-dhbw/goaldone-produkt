package de.goaldone.backend.filter;

import de.goaldone.backend.service.JitProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that performs Just-In-Time (JIT) provisioning for users upon their first request.
 * It intercepts incoming requests, extracts the JWT from the security context, and ensures that
 * a corresponding {@link de.goaldone.backend.entity.UserIdentityEntity} and {@link de.goaldone.backend.entity.UserAccountEntity}
 * exist in the local database, synchronizing them with the external IAM (Zitadel).
 */
@RequiredArgsConstructor
@Slf4j
public class JitProvisioningFilter extends OncePerRequestFilter {

    private final JitProvisioningService jitProvisioningService;

    /**
     * Intercepts the request to trigger JIT provisioning if a JWT authentication is present.
     *
     * @param request the current HTTP request
     * @param response the current HTTP response
     * @param filterChain the filter chain to proceed with
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Only process if SecurityContext has JWT authentication
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = (Jwt) jwtAuth.getPrincipal();
            try {
                jitProvisioningService.provisionUser(jwt);
            } catch (Exception e) {
                log.error("JIT provisioning failed for user {}", jwt.getSubject(), e);
                // Don't fail the request, but log the error for monitoring
            }
        }
        filterChain.doFilter(request, response);
    }
}
