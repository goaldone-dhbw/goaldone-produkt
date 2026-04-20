package de.goaldone.backend.filter;

import de.goaldone.backend.service.JitProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JitProvisioningFilterTest {

    @Mock
    private JitProvisioningService jitProvisioningService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JitProvisioningFilter jitProvisioningFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthentication_skipsProvisioningAndContinues() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(null);

        jitProvisioningFilter.doFilterInternal(request, response, filterChain);

        verify(jitProvisioningService, never()).provisionUser(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonJwtAuthentication_skipsProvisioningAndContinues() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pass")
        );

        jitProvisioningFilter.doFilterInternal(request, response, filterChain);

        verify(jitProvisioningService, never()).provisionUser(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void jwtAuthentication_provisioningThrows_swallowsExceptionAndContinues() throws ServletException, IOException {
        Jwt jwt = buildJwt("test-sub");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        doThrow(new RuntimeException("Test error")).when(jitProvisioningService).provisionUser(jwt);

        jitProvisioningFilter.doFilterInternal(request, response, filterChain);

        verify(jitProvisioningService).provisionUser(jwt);
        verify(filterChain).doFilter(request, response);
    }

    private Jwt buildJwt(String sub) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
