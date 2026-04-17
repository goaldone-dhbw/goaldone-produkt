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

@RequiredArgsConstructor
@Slf4j
public class JitProvisioningFilter extends OncePerRequestFilter {

    private final JitProvisioningService jitProvisioningService;

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
